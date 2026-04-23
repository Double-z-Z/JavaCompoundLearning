package com.example.message;

public class ChatDataEncoder {

    public static byte[] encode(byte[] messageBytes) {
        int messageLength = messageBytes.length;
        byte[] byteData = new byte[4 + messageLength];
        byteData[0] = (byte) (messageLength >> 24);
        byteData[1] = (byte) (messageLength >> 16);
        byteData[2] = (byte) (messageLength >> 8);
        byteData[3] = (byte) messageLength;
        System.arraycopy(messageBytes, 0, byteData, 4, messageLength);
        return byteData;
    }

}
