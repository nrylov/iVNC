/* Connection configuration kept out of diagnostic strings and encrypted when persisted. */
package dev.ivnc.nativeclient;

import org.json.JSONObject;

/** One selected Mac connection; no toString serializes its private fields. */
final class ConnectionProfile {
    final String host,username,password;
    final int port;
    final boolean enhanced,pipeline,remember;
    ConnectionProfile(String host,int port,String username,String password,boolean enhanced,boolean pipeline,boolean remember){
        this.host=host;this.port=port;this.username=username;this.password=password;this.enhanced=enhanced;this.pipeline=pipeline;this.remember=remember;
    }
    boolean matches(String host,int port,String username){return this.host.equals(host)&&this.port==port&&this.username.equals(username);}
    String encryptedInput()throws Exception{
        return new JSONObject().put("host",host).put("port",port).put("username",username).put("password",password)
            .put("enhanced",enhanced).put("pipeline",pipeline).toString();
    }
    static ConnectionProfile parse(JSONObject json)throws Exception{
        return new ConnectionProfile(json.getString("host"),json.getInt("port"),json.getString("username"),json.getString("password"),json.optBoolean("enhanced",true),json.optBoolean("pipeline",true),true);
    }
}
