package com.example.server.db;

import java.util.List;

public interface MessageDBService {
    /**
     * 保存消息到数据库
     */
    void saveMessage(String message);

    /**
     * 查询最近消息
     * @param count 最近消息数量
     * @return 最近消息列表
     */
    List<String> queryRecentMessages(int count);
    
}
