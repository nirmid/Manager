package org.example;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class ManagerClass {
    final private BlockingDeque<File> filesToSplitDeque;
    final private AmazonSQS sqsClient;
    final private AmazonS3 s3Client;
    final private String sqsFromLocalApplicationURL = "https://sqs.us-east-1.amazonaws.com/712064767285/LocalApplicationToManagerS3URLToDataSQS.fifo";

    private boolean terminated = false;
    private ConcurrentHashMap<String,Integer> fileLinesLeftManager;
    public ManagerClass(){
        sqsClient = AmazonSQSClientBuilder.defaultClient();
        s3Client = AmazonS3ClientBuilder.defaultClient();
        filesToSplitDeque = new LinkedBlockingDeque<>();
        fileLinesLeftManager = new ConcurrentHashMap<>();
    }

    public ConcurrentHashMap<String, Integer> getFileLinesLeftManager() {
        return fileLinesLeftManager;
    }

    public void setTerminated(boolean terminated) {
        this.terminated = terminated;
    }
    public boolean isTerminated() {
        return terminated;
    }

    public AmazonS3 getS3Client() {
        return s3Client;
    }

    public BlockingDeque<File> getFilesToSplitDeque() {
        return filesToSplitDeque;
    }
    public AmazonSQS getSqsClient() {
        return sqsClient;
    }

    public String getSqsFromLocalApplicationURL() {
        return sqsFromLocalApplicationURL;
    }

}
