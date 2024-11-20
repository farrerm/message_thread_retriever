package com.mycompany.app;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import okhttp3.*;

import java.time.Instant;

import java.sql.Connection;

public class SlackMessageRetriever {

    private static final String BASE_URL = "https://slack.com/api/";

    //this token has scope for Prefect Community workspace
    static String user_token = ""; // to do

    private static final OkHttpClient client = new OkHttpClient();
    
    //specifies particular channel within workspace
    
    private static final String CHANNEL_ID = ""; // to do

    private static List<JSONObject> thread_ids = new ArrayList<>();
    private static List<SlackMessageThread> threads = new ArrayList<>();

    private static Connection my_con;

    public static void main(String[] args) {
        try {
            my_con = ConnectionClass.connectToDB("", "", ""); // to do
            //use buffered writer, write to file for development
            BufferedWriter out = new BufferedWriter(
            new FileWriter("", true));
            //get the first message from each thread, assign to thread_ids
            System.out.println("getting first messages");
            getFirstMessages(CHANNEL_ID);
            //slack api returns thread ids in reverse chron order
            //to apply our rule based grouping, we want to reverse the order
            Collections.reverse(thread_ids);

            //use first message from each thread to get all messages in each thread
            //append each thread to threads

            //will need to add rate limiting here
            //slack api allows 50 requests per minute
            int count = 0;
            for (JSONObject message: thread_ids) {
                System.out.println("getting thread " + count++);
                sleepSlack();
                JSONArray raw_thread = getThread(message.getString("ts"));
                SlackMessageThread filtered_thread = new SlackMessageThread(raw_thread);
                filtered_thread.computeTopicComplete();
                threads.add(filtered_thread);
            }
            //checkSimilarity() may adjust topics as necessary
            //*******
            checkSimilarity();
            for (SlackMessageThread thread : threads) {
                thread.topic_summary = AzureOpenAI.topicSummary(thread.topic_complete);
            }

            for (SlackMessageThread thread : threads) {
                thread.sentiment = AzureOpenAI.sentimentAnalysis(thread.messagesToString());
            }

            for (SlackMessageThread thread : threads) {
                thread.is_open = AzureOpenAI.isTopicOpen(thread.topic_complete, thread.messagesToString());
            }
            int counts = 1;
            for (SlackMessageThread thread : threads) {
               // MessageThread temp = new MessageThread();
                out.write(thread.toString());
                out.write("\n");
                out.flush();
                System.out.println("writing thread no " + counts++ + " to db");
                //write to db
                ConnectionClass.insertSlackThread(my_con, "slack_threads", thread.thread_id, thread.author, thread.timestamp, thread.topic_summary, thread.topic_complete, thread.sentiment, thread.is_open, thread.messages);
            }
            out.close();
        
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sleepSlack() {
        //to comply with Slack API rate limits.
        int rate_limit = 49;
        int request_interval_milliseconds = 60000 / rate_limit;
        try {
            Thread.sleep(request_interval_milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void checkSimilarity() {
        //we want to merge adjacent threads by the same author
        //can use a map to store last thread for each author
        try {
            Map<String, Integer> last_thread_map = new HashMap<>();
            for (int i = 0; i < threads.size(); i++) { 
                SlackMessageThread cur_thread = threads.get(i);
                String cur_author = cur_thread.author;
                if (!last_thread_map.containsKey(cur_author)) {
                    last_thread_map.put(cur_author, i);
                } else {
                    int last_thread_index = last_thread_map.get(cur_author);
                    SlackMessageThread last_thread = threads.get(last_thread_index);
                    String topic_1 = cur_thread.topic_complete;
                    String topic_2 = last_thread.topic_complete;

                    String result = AzureOpenAI.similarityComparison(topic_1, topic_2);

                    String answer = AzureOpenAI.parseResponse(result);

                    if (answer.equals("yes")) {
                        //merge the threads
                        mergeThreads(last_thread_index, i);
                        last_thread.merge_explanation = result;
                        //mergeThreads will effectively remove the second thread
                        i--;
                        //we don't need to update the map, it's still correct
                    } else {
                        //do not merge the threads
                        //in this case update the map
                        last_thread_map.put(cur_author, i);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void mergeThreads(int last_thread_index, int i) {
        try {
            SlackMessageThread last_thread = threads.get(last_thread_index);
            SlackMessageThread cur_thread = threads.get(i);

            //merge the messages
            last_thread.messages.addAll(cur_thread.messages);

            // Sort the list based on the "timestamp" field
            last_thread.messages.sort((t1, t2) -> {
                try {
                    float timestamp1 = Float.parseFloat(t1.getString("timestamp"));
                    float timestamp2 = Float.parseFloat(t2.getString("timestamp"));
                    return Float.compare(timestamp1, timestamp2);
                } catch (JSONException e) {
                    e.printStackTrace();
                    return 0; // Default to equal if there's an error
                }
            });
            //remove second thread from threads
            threads.remove(i);
            //update the topic
            last_thread.computeTopicComplete();
           // last_thread.topic_summary = AzureOpenAI.topicSummary(last_thread.topic_complete);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //this method retrieves the first message from each thread
    private static void getFirstMessages(String channelId) {
        List<JSONObject> allMessages = new ArrayList<>();
        String cursor = null;

        do {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "conversations.history").newBuilder()
                    .addQueryParameter("channel", channelId)
                    .addQueryParameter("limit", "1000");  // Adjust as needed, max is 1000

            if (cursor != null) {
                urlBuilder.addQueryParameter("cursor", cursor);
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Authorization", "Bearer " + user_token)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);

                if (!jsonResponse.getBoolean("ok")) {
                    System.err.println("Error: " + jsonResponse.getString("error"));
                    break;
                }

                JSONArray messages = jsonResponse.getJSONArray("messages");

                String cur_time = curTime();
                float cur_time_float = Float.parseFloat(cur_time);

                for (int i = 0; i < messages.length(); i++) {
                    //only add the messages up to 10 days old
                    String ts = messages.getJSONObject(i).getString("ts");
                    float ts_float = Float.parseFloat(ts);
                    //we only want the last 10 days of data
                    if (cur_time_float - ts_float > 864000) {
                        break;
                    }
                    allMessages.add(messages.getJSONObject(i));
                }

                if (jsonResponse.has("response_metadata") && 
                    jsonResponse.getJSONObject("response_metadata").has("next_cursor")) {
                    cursor = jsonResponse.getJSONObject("response_metadata").getString("next_cursor");
                } else {
                    cursor = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        } while (cursor != null && !cursor.isEmpty());

        SlackMessageRetriever.thread_ids = allMessages;
    }

    //after we have the first message from a thread, we can call this method
    //to get all of the thread messages as a list
    private static JSONArray getThread(String parentTimestamp) throws IOException {
        Request request = new Request.Builder()
                .url(BASE_URL + "conversations.replies?channel=" + CHANNEL_ID + "&ts=" + parentTimestamp)
                .addHeader("Authorization", "Bearer " + user_token)
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();

        try {
            JSONObject jsonResponse = new JSONObject(responseBody);

            if (!jsonResponse.getBoolean("ok")) {
                throw new IOException("Error fetching thread replies: " + jsonResponse.getString("error"));
            }

            JSONArray thread = jsonResponse.getJSONArray("messages");
            //add the resulting thread to threads

            return thread;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    private static String curTime() {
        // Get the current timestamp
        Instant now = Instant.now();

        // Extract seconds and nanoseconds
        long seconds = now.getEpochSecond();
        int nanos = now.getNano();

        // Convert nanoseconds to microseconds (6 decimal places)
        double microseconds = nanos / 1000.0;

        // Format the timestamp
        String timestamp = String.format("%d.%06d", seconds, (int)microseconds);
        return timestamp;
    }
}