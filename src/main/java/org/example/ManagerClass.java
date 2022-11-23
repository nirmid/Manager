package org.example;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class ManagerClass {
    final private BlockingDeque<File> filesToSplitDeque;
    final private AmazonSQS sqsClient;
    final private AmazonS3 s3Client;
    final private AmazonEC2 ec2Client;
    final private String sqsFromLocalApplicationURL = "https://sqs.us-east-1.amazonaws.com/712064767285/LocalApplicationToManagerS3URLToDataSQS.fifo";
    final private String uploadBucket = "amazon-first-project";
    final private int workerToThreadRatio = 5;

    final private String workerAmiId = "ami-0fce5e44348f5a184";


    private List<Thread> threadList;

    private boolean terminated = false;
    private ConcurrentHashMap<String,File> fileIDHashmap;
    public ManagerClass(){
        sqsClient = AmazonSQSClientBuilder.defaultClient();
        s3Client = AmazonS3ClientBuilder.defaultClient();
        filesToSplitDeque = new LinkedBlockingDeque<>();
        fileIDHashmap = new ConcurrentHashMap<>();
        ec2Client = AmazonEC2ClientBuilder.defaultClient();
        threadList = new ArrayList<>();
    }

    public String getWorkerAmiId() {
        return workerAmiId;
    }

    public AmazonEC2 getEc2Client() {
        return ec2Client;
    }

    public void addToThreadList(Thread thread){
        this.threadList.add(thread);
    }
    public List<Thread> getThreadList(){
        return threadList;
    }

    public String getUploadBucket() {
        return uploadBucket;
    }

    public int getWorkerToThreadRatio() {
        return workerToThreadRatio;
    }

    public ConcurrentHashMap<String, File> getFileIDHashmap() {
        return fileIDHashmap;
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
