package com.example.server.message.response;

import com.example.server.message.base.Message;

import java.util.List;

public class UserListMessage extends Message {
    
    private List<String> users;
    private int count;
    
    public UserListMessage() {
        super();
        this.type = MessageType.USER_LIST;
    }
    
    public UserListMessage(List<String> users) {
        this();
        this.users = users;
        this.count = users != null ? users.size() : 0;
    }
    
    public List<String> getUsers() {
        return users;
    }
    
    public void setUsers(List<String> users) {
        this.users = users;
        this.count = users != null ? users.size() : 0;
    }
    
    public int getCount() {
        return count;
    }
    
    public void setCount(int count) {
        this.count = count;
    }
    
    @Override
    public String toString() {
        return "UserListMessage{" +
                "users=" + users +
                ", count=" + count +
                ", timestamp=" + timestamp +
                '}';
    }
}
