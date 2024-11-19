package com.mycompany.app;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

import java.sql.Connection;

public class DiscordMessageRetriever {
  //This code can be used to scrape Discord messages from a channel
  private static final String DISCORD_API_BASE_URL = "https://discord.com/api/v9";

  private static String lastMessageID = "";
  private static Connection my_con;
  private static int counter;
  private static String mainChannelId = ""; // put channel id here
  public static void main(String[] args) {

    my_con = ConnectionClass.connectToDB("", "", "");  //db connection
    
    //we can retrieve the channel id by accessing the channel in developer mode
    String channelId = ""; //to do
    
    String token = ""; //to do
    
    counter = 0;
    System.out.println(lastMessageID);
    System.out.println(DiscordMessageRetriever.lastMessageID);
    
    try {
      //we will write the messages to a file
      BufferedWriter out = new BufferedWriter(
        new FileWriter("", true)); //put file location if desired.
      out.write("messages = [");
      out.write("\n");
      out.flush();
      startChannelSearch(channelId, token);
      //insert dummy json at end of list
      //to take care of trailing comma
      JSONObject nu_json = new JSONObject();
      out.write(prettyPrintJsonUsingDefaultPrettyPrinter(nu_json.toString()));
      out.write("\n");
      out.write("]");
      out.flush();
      out.close();
    }
    catch (IOException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
    System.out.println(counter);
  }
  
  public static void startChannelSearch(String channelId, String token) {

    while (true) {
      try {
          BufferedWriter out = new BufferedWriter(
            new FileWriter("", true)); //put file location if desired.
          JSONArray messages = getAllMessagesFromChannel(channelId, token, DiscordMessageRetriever.lastMessageID);
          if (messages.length() == 0) {
            break;
          }
      
          for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            //Discord's thread feature treats messages that are part of
            //a thread as a separate channel, with its own channel id
            if (message.has("thread")){
              System.out.println("has a thread!");
              JSONObject small_json = message.getJSONObject("thread");
              String nu_channel_id = small_json.getString("id");
              System.out.println("nu channel id " + nu_channel_id);
              String temp = DiscordMessageRetriever.lastMessageID;
              DiscordMessageRetriever.lastMessageID = "";
              //we recursively call startChannelSearch to get the messages in the thread
              startChannelSearch(nu_channel_id, token);
              DiscordMessageRetriever.lastMessageID = temp;
            }
            //parse message, write fields to db
            String author = message.getJSONObject("author").getString("username");
            String content = message.getString("content");
            content = formatForPosgres(content);
            String timestamp = message.getString("timestamp");
            String message_id = message.getString("id");
            String channel_id = message.getString("channel_id");
            String referenced_message_id = null;
            if (message.has("referenced_message")){
              JSONObject small_json = message.getJSONObject("referenced_message");
              referenced_message_id = small_json.getString("id");
            }
            //uncomment this if we want to write to db
           // connection_class.insertRow(my_con, "my_first_table", author, timestamp, content);

            JSONObject nu_json = new JSONObject();
            nu_json.put("author", author);
            nu_json.put("timestamp", timestamp);
            nu_json.put("content", content);
            nu_json.put("message_id", message_id);
            nu_json.put("channel_id", channel_id);
            if (referenced_message_id != null){
              nu_json.put("referenced_message_id", referenced_message_id);
            }

            out.write(prettyPrintJsonUsingDefaultPrettyPrinter(nu_json.toString()));
            out.write(',');
            out.write("\n");
            out.flush();
            counter++;
          }
          out.close();
          System.out.println(DiscordMessageRetriever.lastMessageID);
          System.out.println("success");
        } catch (Exception e) {
          e.printStackTrace();
      }
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

  public static String formatForPosgres(String input){
    input = input.replaceAll("'", "''");
    return input;
  }

  private static JSONArray getAllMessagesFromChannel(String channelId, String token, String lastMessageID) throws IOException {
    OkHttpClient client = new OkHttpClient();
    Request request = null;
    if (lastMessageID == "") {
        request = new Request.Builder()
          .url(DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages")
          .header("Authorization", token)
          .build();
    } else {
        request = new Request.Builder()
          .url(DISCORD_API_BASE_URL + "/channels/" + channelId + "/messages?before=" + lastMessageID)
          .header("Authorization", token)
          .build();
    }
        try (Response response = client.newCall(request).execute()) {
          if (!response.isSuccessful()) {
            throw new IOException("Unexpected response code: " + response);
          }
          
          JSONArray responseBody = new JSONArray(response.body().string());
          JSONObject lastMessage = responseBody.getJSONObject(responseBody.length() - 1);
          DiscordMessageRetriever.lastMessageID = lastMessage.getString("id");
          return responseBody;
        }
        catch(Exception e){
          System.out.println("Error in getAllMessagesFromChannel");
          System.out.println(e.getMessage());
          return new JSONArray();
        }
    } 
}
