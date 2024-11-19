package com.mycompany.app;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

public class AzureOpenAI {
  
    private static final String OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";

    private static final String API_KEY = "####################";
  
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient client = new OkHttpClient();

    public static String similarityComparison(String topic_1, String topic_2) throws IOException {

        //comply with rate limits
        sleepOpenAI();

        String system_prompt = "You are an AI assistant that measures similarity between two topics.  You will be given two strings, where each string represents a topic.  By topic, what I mean is that the content of each string is about a particular subject.  Your task is to determine whether the two strings are about the same subject.  Note that just because the two topics both ask questions about ########, that will not be enough to make them about the same topic.  Rather, two topics are about the same subject only if they both discuss the same specific subject about ########." +  

        "##############Examples##############" + 

        "##############Example Number 1################" +

        "Topic 1: " +

        "Hi team! So flow retrying and persistence are confusing me. When a flow fails and I hit retry, it says something akin to 'Tasks without persisted results will be rerun.'" +

        "Great, I think, because when I click on my tasks I can see `Result with value None persisted to ######.` clearly in the sidebar. However, ###### is rerunning all those tasks which say that their return value (None being correct) is persisted. What am I missing here?" +

        "Topic 2:" +

        "For feedback on the UI graph view when running a task, it would be great to try and separate out vertical and horizontal zooming. Any task which takes more than a few seconds to run requires zooming out on the chart, which means you just get a ThinGreenLine. Most timeseries charts (such as finance tickers, grafana, etc) allow you to zoom in and out on the time range while keeping the y axis autoscaled. Obviously that wont work nicely for prefect with many many tasks, but I feel like there has to be a better solution than the current implementation." +

        "I'd like to suggest a default behaviour of 'zooming out stops impacting the y-axis and only impacts the time axis when the min and max vertical extents are visible in the plot'." +

        "This should cater for zooming in and out of complex tasks where you may want to visualise hundreds of task runs, and also allow users to 'find' the plots if they manage to pan their chart away from where things are (as the min/max condition is false they could zoom out until seeing the plot, and then zoom back in)" +
        "I've always found the scaling section there doesnt really work. Hitting 'Reset' doesnt do anything useful, and hitting the `-` a few times seems to take the plot to a section of the canvas with nothing on it. Here's a video recording showing these issues" +
        "+ and - should change zoom level (ideally horizontal zoom level), not pan the plot. You can see how at the end hitting `-` kept taking me further ahead in time, even though there were no tasks there. Zooming out shouldnt move tasks off screen. Similarly, the reset button I would expect to reset the view so that the collection of tasks fill the horizontal and vertical space, but it doesnt seem to do this. Can you shed some light on what Reset is intended to do?" +
        "<https://github.com/#######/issues/14709>" +

        "###########end of example Number 1 Topic 2####################" +

        "Analysis: Although Topic 1 and Topic 2 both discuss issues with #######, they are not about the same specific topic.  Topic 1 is about flow retrying and persistence, while Topic 2 is about zooming in and out of the UI graph view.  Thus, in this case your answer should be:" +

        "no" +

        "##############End of example Number 1###########" +

        "##############Example Number 2################" +

        "Topic 1: " +

        "I’m having trouble logging in.  I think maybe I forgot my password?" +

        "Topic 2: " + 

        "Is there a way to reset my password?" +

        "###########End of Example Number 2 Topic 2" + 

        "Analysis: Topic 1 and Topic 2 are about the same specific topic, which is the user's password.  Thus, in this case your answer should be:" +

        "yes" + 

        "##############End of example Number 2####################" +

        "##############Example Number 3################" +

        "Topic 1: " +

        "I’m having trouble logging in.  I think maybe I forgot my password?" +

        "Is there a way to reset my password?" +

        "Topic 2: " + 

        "Could someone tell me the web address for the home page?" +

        "Oh, never mind, I figured it out!  It\u2019s <http://www.homepage.com|www.homepage.com>." +

        "###########End of Example Number 3 Topic 2###################" + 

        "Analysis: Topic 1 and Topic 2 example Number 2 are about different topics.  Topic 1 is about the user's password.  On the other hand, Topic 2 is about the web address for the home page.  Thus, in this case your answer should be:" +

        "no" + 

        "##############End of example Number 3####################" +

        "Please include a brief explanation of how you arrived at your answer.  The answer should be printed as a single word on a separate line at the end of your response, and should be either 'yes' or 'no'.  'yes' will mean that the two strings are about the same subject, while 'no' will mean that they are not.";

        String user_prompt = "Topic 1: \n" + topic_1 + "\nTopic 2: \n" + topic_2;

        try {
            // Create the request body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-3.5-turbo");
          
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", system_prompt);
            messagesArray.put(systemMessage);
          
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", user_prompt);
            messagesArray.put(userMessage);
            
            jsonBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            // Build the request
            Request request = new Request.Builder()
                    .url(OPENAI_ENDPOINT)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute(); 
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Parse and return the response
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Error fetching thread replies: " + e.getMessage());
        }
    }

    public static String parseResponse(String response) {
        String[] lines = response.split("\n");
        String ret_val = lines[lines.length - 1];
        return ret_val;
    }

    public static String topicSummary(String topic_1) throws IOException {

        sleepOpenAI();

        String system_prompt = "You are an AI assistant that reads a topic and provides a one sentence summary.  When creating the summary, keep in mind that the topics in question were written not by me, but by various users. The summary should be a concise representation of the topic, and should be written in a way that is easy to understand.  The summary is not an answer to the topic, but rather a concise representation of it.";

        String user_prompt = topic_1;

        try {
            // Create the request body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-3.5-turbo");
          
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", system_prompt);
            messagesArray.put(systemMessage);
          
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", user_prompt);
            messagesArray.put(userMessage);
            
            jsonBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            // Build the request
            Request request = new Request.Builder()
                    .url(OPENAI_ENDPOINT)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute(); 
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Parse and return the response
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Error fetching thread replies: " + e.getMessage());
        }
    }

    public static String sentimentAnalysis(String thread) throws IOException {

        sleepOpenAI();

        String system_prompt = "You are an AI assistant that reads a message thread and provides a sentiment analysis.  The thread will be a series of messages, where each message may be written by a different user.  The thread will be passed as a list of JSONObjects with the following fields: user, timestamp, and content.  Your task is to determine the overall sentiment of the thread.  The sentiment should be one of the following: positive, negative, or neutral.  For this task, there is no need to include an explanation of how you arrived at your answer.  The answer should be printed as a single word.";

        String user_prompt = thread;

        try {
            // Create the request body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-3.5-turbo");
          
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", system_prompt);
            messagesArray.put(systemMessage);
          
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", user_prompt);
            messagesArray.put(userMessage);
            
            jsonBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            // Build the request
            Request request = new Request.Builder()
                    .url(OPENAI_ENDPOINT)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute(); 
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Parse and return the response
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Error fetching thread replies: " + e.getMessage());
        }
    }

    public static String isTopicOpen(String topic, String thread) throws IOException {

        sleepOpenAI();

        String system_prompt = "You are an AI assistant that reads a message thread topic, and then the entire the thread, and determines whether the topic is still open.  The topic will be a string, and the thread will be a series of messages, where each message may be written by a different user.  A topic can be thought of as a question or a request for help.  If the topic is still open, this means that the answer or help has not been provided in the thread.  In that case, you should respond with the answer 'open'.  On the other hand, if the question raised by the topic is answered in the thread, then the topic is considered closed, and you should respond with the answer 'closed'.  Please note that just because there are responses to the inital message (i.e. there is more than a single message in the thread), this does not necessarily mean that the thread is closed.  In making your determination, consider whether whatever problem described by the topic has been solved with a stated solution in the thread.  However, you should only consider information provided in the thread.  Do not consider any facts or circumstances external to the thread itself.  For this task, it is not necessary to explain your reasoning.  Please provide your answer as a simple 'open' or 'closed'.";

        String user_prompt = "Topic: \n" + topic + "\n" + "Thread: \n" + thread;

        try {
            // Create the request body
            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "gpt-3.5-turbo");
          
            JSONArray messagesArray = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", system_prompt);
            messagesArray.put(systemMessage);
          
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", user_prompt);
            messagesArray.put(userMessage);
            
            jsonBody.put("messages", messagesArray);

            RequestBody body = RequestBody.create(jsonBody.toString(), JSON);

            // Build the request
            Request request = new Request.Builder()
                    .url(OPENAI_ENDPOINT)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            // Execute the request
            Response response = client.newCall(request).execute(); 
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Parse and return the response
            String responseBody = response.body().string();
            JSONObject jsonResponse = new JSONObject(responseBody);
            JSONArray choices = jsonResponse.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            return message.getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            throw new IOException("Error fetching thread replies: " + e.getMessage());
        }
    }

    public static void sleepOpenAI() {
        //to comply with OpenAI rate limits.
        int rate_limit = 499;
        int request_interval_milliseconds = 60000 / rate_limit;
        try {
            Thread.sleep(request_interval_milliseconds);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

