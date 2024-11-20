package com.mycompany.app;

import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import org.json.JSONException;

public class SlackMessageThread {
    
    public String thread_id;
    public String author;
    public String topic_complete;
    public String topic_summary;
    public String sentiment;
    public String is_open;
    public String timestamp;
    //for debugging
    public String merge_explanation;
    public List<JSONObject> messages = new ArrayList<>();

    SlackMessageThread(JSONArray raw_thread) {
        
        if (raw_thread.length() == 0) {
            throw new IllegalArgumentException("Thread is empty");
        }
        try {
            // use message id from first message as thread_id
            JSONObject first_message = raw_thread.getJSONObject(0);

           // System.out.println(raw_thread.getJSONObject(0).toString(2));
            
           //we can use timestamp as thread id
           //timestamp is unique for all Slack messages.
            this.thread_id = first_message.getString("ts");
            this.author = first_message.getString("user");
            this.timestamp = first_message.getString("ts");
            this.merge_explanation = "";

            //construct message list
            for (int i = 0; i < raw_thread.length(); i++) {
                JSONObject unfiltered_message = raw_thread.getJSONObject(i);
                String usr = unfiltered_message.getString("user");
                String timestamp = unfiltered_message.getString("ts");
                String text = unfiltered_message.getString("text");
                JSONObject temp = new JSONObject();
                temp.put("user", usr);
                temp.put("timestamp", timestamp);
                temp.put("text", text);
                this.messages.add(temp);
            }
        } catch (JSONException e) {
           // System.out.println(raw_thread.getJSONObject(0).toString(2));
           throw new RuntimeException("Error parsing thread");
            //e.printStackTrace();
        }
    }

    public void computeTopicComplete() {
        try {
            String temp = "";
            temp += messages.get(0).getString("text");
            String author = messages.get(0).getString("user");

            //we will say that a topic is the text from 
            //the first message, plus any other messages
            //by the same author in the thread
            for (int i = 1; i < messages.size(); i++) {
                JSONObject message = messages.get(i);
                if (message.getString("user").equals(author)) {
                    temp += "\n" + message.getString("text");
                } 
            }
            this.topic_complete = temp;
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RuntimeException("Error computing topic");
        } 
    }

    public String messagesToString() {
        try {
            String ret_val = "";
            ret_val += "[\n";
            for (JSONObject message : this.messages) {
                ret_val += message.toString(2) + "\n";
            }
            ret_val += "]\n";
            return ret_val;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String toString() {
        try {
            String ret_val = "";
            
            ret_val += "[\n";
            ret_val += "thread_id:\n";
            ret_val += this.thread_id + "\n";

            ret_val += "author:\n";
            ret_val += this.author + "\n";

            ret_val += "timestamp:\n";
            ret_val += this.timestamp + "\n";

            ret_val += "sentiment:\n";
            ret_val += this.sentiment + "\n";

            ret_val += "topic open?:\n";
            ret_val += this.is_open + "\n";

            ret_val += "topic summary:\n";
            ret_val += this.topic_summary + "\n";

            ret_val += "merge explanation:\n";
            ret_val += this.merge_explanation + "\n";

            ret_val += "complete topic:\n";
            ret_val += this.topic_complete + "\n";

            for (JSONObject message : this.messages) {
                ret_val += message.toString(2) + "\n";
            }

            ret_val += "]\n";

            return ret_val;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}

