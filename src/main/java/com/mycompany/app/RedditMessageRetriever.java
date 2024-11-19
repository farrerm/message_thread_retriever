package com.mycompany.app;

import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

public class RedditMessageRetriever {
    static String USERNAME = ""; //to do
    static String PASSWORD = ""; //to do
    static String CLIENT_ID = "";  //to do  
    static String CLIENT_SECRET = ""; //to do
    static String USER_AGENT = ""; //to do
    static OkHttpClient client = new OkHttpClient();

    private static final int REQUESTS_PER_MINUTE = 100;
    private static final long MINUTE_IN_MILLIS = 60 * 1000;

    private static void rateLimit() {
        try {
            Thread.sleep((MINUTE_IN_MILLIS + 1) / REQUESTS_PER_MINUTE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main (String[] args) {
        Connection my_con = connection_class.connect_to_db("", "", ""); //to do
        getThreads(my_con, "dataengineering"); //insert subreddits as desired
        getThreads(my_con, "python");
    }

    //we can consider getting threads from subreddits dataengineering and python
    public static void getThreads(Connection my_con, String subredditName) {      
    
        try {
            // BufferedWriter out = new BufferedWriter(
            //     new FileWriter("", true));
            BufferedWriter out_raw = new BufferedWriter(
                new FileWriter("", true)); //write to file if desired
            String accessToken = getAccessToken();
     
            List<RedditMessageThread> all_threads_in_channel = new ArrayList<>();
            List<String> thread_ids = getThreadIdsInSubreddit(accessToken, subredditName);
            System.out.println("thread_ids.size()");

            double current_time = curTime();
            long current_time_long = (long) current_time;
            long ten_days_in_seconds = 10 * 24 * 60 * 60;
    
            for (String id : thread_ids){
                //List<JSONObject> thread = new ArrayList<JSONObject>();
                System.out.println(id);
                rateLimit();
                String thread_contents = getThreadContents(accessToken, id);

                //here is where we can check timestamp
               // System.out.println(thread_contents);
                JSONArray thread_body = new JSONArray(thread_contents);
                JSONObject first_message = thread_body.getJSONObject(0);
                if !(first_message.has("data")){
                    continue;
                }
                JSONObject data = first_message.getJSONObject("data");
                if !(data.has("children")){
                    continue;
                }
                JSONArray children = data.getJSONArray("children");
                JSONObject first_child = children.getJSONObject(0);
                if !(first_child.has("data")){
                    continue;
                }        
                JSONObject first_child_data = first_child.getJSONObject("data");
                if !(first_child_data.has("created_utc")){
                    continue;
                }            
                double timestamp = first_child_data.getDouble("created_utc");
                long timestamp_long = (long) timestamp;
                //only consider threads that are less than 10 days old
                if (current_time_long - timestamp_long < ten_days_in_seconds){
                    RedditMessageThread filtered_thread = new RedditMessageThread(thread_body);   
                filtered_thread.computeTopicComplete();
                // can limit search to topics of interest
                if (filtered_thread.topic_complete.contains("Prefect")){
                    filtered_thread.thread_id = first_child_data.getString("id");
                    filtered_thread.subreddit = subredditName;
                    filtered_thread.url = first_child_data.getString("url");
                    filtered_thread.author = first_child_data.getString("author");
                    filtered_thread.timestamp = first_child_data.getString("created_utc");
                    filtered_thread.topic_summary = AzureOpenAI.topicSummary(filtered_thread.topic_complete);
                    filtered_thread.sentiment = AzureOpenAI.sentimentAnalysis(filtered_thread.messagesToString());
                    filtered_thread.is_open = AzureOpenAI.isTopicOpen(filtered_thread.topic_complete, filtered_thread.messagesToString());
                
                    all_threads_in_channel.add(filtered_thread);
                    out_raw.write(prettyPrintJsonUsingDefaultPrettyPrinter(thread_body.toString()));
                    out_raw.write("\n");
                }
            }
            else {
                break;
            }
        
            System.out.println(thread_ids.size());
            System.out.println(all_threads_in_channel.size());
            writeToDB(my_con, subredditName, all_threads_in_channel);
            printToFile(all_threads_in_channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    } 
    private static void writeToDB(Connection my_con, String subreddit, List<RedditMessageThread> all_threads_in_channel){
       
        for (RedditMessageThread thread : all_threads_in_channel){
            System.out.println(thread.toString());
            connection_class.insertRedditThread(my_con, "reddit_threads", thread.thread_id, subreddit, thread.author, thread.timestamp, thread.topic_summary, thread.url, thread.topic_complete, thread.sentiment, thread.is_open, thread.messages);
        }
    }
    
    private static List<String> getThreadIdsInSubreddit(String accessToken, String subredditName) throws IOException {

        List<String> threadIds = new ArrayList<>();
        HashSet<String> threadIdsSet = new HashSet<>();
        String after = null;
        int requestCount = 0;

        do {
            requestCount++;
            System.out.println("Making request " + requestCount + " for subreddit: " + subredditName);
            String url = "https://oauth.reddit.com/r/" + subredditName + "/new?limit=100";
            if (after != null) {
                url += "&after=" + after;
            }

            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .header("User-Agent", USER_AGENT)
                .build();

            try  {
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONObject data = jsonResponse.getJSONObject("data");
                JSONArray children = data.getJSONArray("children");
                
                for (int i = 0; i < children.length(); i++) {
                    String id = children.getJSONObject(i).getJSONObject("data").getString("id");
                    int oldSize = threadIdsSet.size();
                    threadIdsSet.add(id);
                    int newSize = threadIdsSet.size();
                    if (oldSize + 1 == newSize) {
                        threadIds.add(id);
                        System.out.println(threadIdsSet.size());
                        System.out.println(threadIds.size());
                    }
                    else{
                        return threadIds;
                    }
                }
                after = data.optString("after", null);
            }
            catch (Exception e) {
                e.printStackTrace();
                break;
            }
        } while (after != null && !after.isEmpty());
        return threadIds;
    }

    private static String getThreadContents(String accessToken, String threadId) throws IOException {
        String url = "https://oauth.reddit.com/comments/" + threadId;
  
        Request request = new Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + accessToken)
            .header("User-Agent", USER_AGENT)
            .build();
  
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            return response.body().string();
        }
    }

    private static void printToFile(List<RedditMessageThread> all_threads_in_channel){
        try {
            BufferedWriter out = new BufferedWriter(
                new FileWriter("", true));//write to file if desired
        
            out.write("messages = [");
            out.write("\n");
            out.flush();
            for (int i = 0; i < all_threads_in_channel.size(); i++) {
                 out.write("[");
                 out.write("\n");
                 RedditMessageThread thread = all_threads_in_channel.get(i);
                 out.write(thread.toString());
                out.write("\n");
                out.flush();
                out.write("]");
                out.write("\n");
            }
            out.write("\n");
            out.write("]");
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String prettyPrintJsonUsingDefaultPrettyPrinter(String uglyJsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            Object jsonObject = objectMapper.readValue(uglyJsonString, Object.class);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
            return prettyJson;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String getAccessToken() throws Exception {
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "password")
                .add("username", USERNAME)
                .add("password", PASSWORD)
                .build();

        Request request = new Request.Builder()
                .url("https://www.reddit.com/api/v1/access_token")
                .header("Authorization", Credentials.basic(CLIENT_ID, CLIENT_SECRET))
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build();
        System.out.println("trying to call Reddit");
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            System.out.println("returning access token");
            return jsonResponse.getString("access_token");
        }
    }

    private static double curTime() {
        // Get the current timestamp
        Instant now = Instant.now();
        // Extract seconds and nanoseconds
        long seconds = now.getEpochSecond();
        // Convert to scientific notation
        String formattedTime = String.format("%.9E", (double) seconds);
        return Double.parseDouble(formattedTime);
    }
}

