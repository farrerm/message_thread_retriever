package com.mycompany.app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import org.postgresql.util.PGobject;

import java.util.*;

//Created this class to allow connection to postgres database on localhost
public class ConnectionClass {
  public static void main(String[] args) {
 
    Connection con = connectToDB("","",""); //to do

  }
  public static Connection connectToDB(String dbname, String user, String pass)
    {
        Connection con_obj=null;
        String url="jdbc:postgresql://localhost:5432/";
        try
        {
            con_obj= DriverManager.getConnection(url+dbname,user,pass);
            if(con_obj!=null)
            {
                System.out.println("Connection established successfully !");
            }
            else
            {
                System.out.println("Connection failed !!");
            }
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
        return con_obj;
    }
    public static void insertRow(Connection con,String tName,String author,String timestamp,String content)
    {
        Statement stmt;
        try
        {
            String query=String.format("insert into %s(usr,time,msg) values('%s','%s', E'%s');",tName,author,timestamp,content);
            stmt=con.createStatement();
            stmt.executeUpdate(query);
            System.out.println("Inserted successfully !");
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
    public static void insertSlackThread(Connection con,String tName, String thread_id, String author, String timestamp, String topic_summary, String topic_complete, String sentiment, String is_open, List<JSONObject> messages)
    {
        Statement stmt;
        try
        {
            JSONArray messagesArray = new JSONArray(messages);
            String JSONString = messagesArray.toString();
            JSONString = formatForPostgres(JSONString);

            String query=String.format("insert into %s(thread_id,author,timestamp,topic_summary,topic_complete,sentiment,is_open, thread_contents) values('%s','%s','%s','%s','%s','%s','%s', '%s'::jsonb);",tName,thread_id,author,timestamp,formatForPostgres(topic_summary), formatForPostgres(topic_complete),sentiment,is_open, JSONString);
            stmt=con.createStatement();
            stmt.executeUpdate(query);
            System.out.println("Inserted successfully !");
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
    public static void insertRedditThread(Connection con,String tName, String thread_id, String subreddit, String author, String timestamp, String topic_summary, String url, String topic_complete, String sentiment, String is_open, List<JSONObject> messages)
    {
        Statement stmt;
 
        try
        {
            JSONArray messagesArray = new JSONArray(messages);
            String JSONString = messagesArray.toString();
            JSONString = formatForPostgres(JSONString);
            String query=String.format("insert into %s(thread_id,subreddit,author,timestamp,topic_summary,link,topic_complete,sentiment,is_open, thread_contents) values('%s','%s','%s','%s','%s','%s','%s','%s','%s', '%s'::jsonb);",tName,thread_id,subreddit,author,timestamp,formatForPostgres(topic_summary), url, formatForPostgres(topic_complete),sentiment,is_open, JSONString);
            stmt=con.createStatement();
            stmt.executeUpdate(query);
            System.out.println("Inserted successfully !");
        }
        catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
    public static String formatForPostgres(String input){
        return input.replace("'", "''");
    }
}
