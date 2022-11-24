package org.example;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;

import java.io.*;
import java.util.*;
import java.util.concurrent.BlockingDeque;

public class S3DownloaderAndWorkerInitiliazer implements Runnable{
    private AmazonSQS sqsClient;
    final private int maximumWorkers = 18;
    private AmazonS3 s3Client;
    private ManagerClass manager;
    private String sqsToManagerUrl;
    private AmazonEC2 ec2Client;
    public S3DownloaderAndWorkerInitiliazer(ManagerClass manager){
        this.manager = manager;
        this.sqsClient = manager.getSqsClient();
        this.sqsToManagerUrl = manager.getSqsFromLocalApplicationURL();
        this.s3Client = manager.getS3Client();
        this.ec2Client = manager.getEc2Client();
    }
    public void run() {
        while(!manager.isTerminated()) {
            try {
                List<Message> messages = getMessagesFromSQS();
                downloadFromS3(messages);
                for (Message message : messages) {
                    int numOfWorkersNeeded = Integer.parseInt(message.getMessageAttributes().get("workers").getStringValue());
                    initWorkers(numOfWorkersNeeded);
                }
                insertToFilesToSplitDeque(messages);
                deleteMessagesFromToManagerSQS(messages);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    public List<Message> getMessagesFromSQS() throws InterruptedException {
        Message message = null;
        ReceiveMessageRequest request = new ReceiveMessageRequest()
                .withQueueUrl(sqsToManagerUrl)
                .withMaxNumberOfMessages(10)
                .withMessageAttributeNames("All");
        List<Message> messages = null;
        while (messages == null) {
            messages = sqsClient.receiveMessage(request).getMessages();
            Thread.sleep(5000);
        }
        return messages;
    }
    public void downloadFromS3(List<Message> messages){
        String home = System.getProperty("user.home");
        for (Message message : messages){
            if (message.getMessageAttributes().get("TERMINATE") != null){
                this.manager.setTerminated(true);
            }
            else {
                String messageS3Path = message.getMessageAttributes().get("path").getStringValue();
                String outputPath = "Output/" + messageS3Path;
                String bucket = message.getMessageAttributes().get("bucket").getStringValue();
                String id = message.getMessageAttributes().get("id").getStringValue();
                File outputFile = new File(home + "/IdeaProjects/Manager/src/main/java/Output/" + id + ".txt");
                s3Client.getObject(new GetObjectRequest(bucket, outputPath), outputFile);
            }
        }
    }
    public void initWorkers(int numOfWorkersToRun){
        int currentWorkers = countWorkers();
        int numOfWorkersAllowedToAdd = maximumWorkers - currentWorkers;
        int numOfWorkersNeededToAdd = numOfWorkersToRun - currentWorkers;
        int numOfWorkersToInit = Math.min(numOfWorkersAllowedToAdd,numOfWorkersNeededToAdd);
        if(numOfWorkersToInit > 0){
            RunInstancesRequest runRequest = new RunInstancesRequest()
                    .withImageId(manager.getWorkerAmiId())
                    .withInstanceType(InstanceType.T2Micro)
                    .withMaxCount(numOfWorkersToInit)
                    .withMinCount(numOfWorkersToInit)
                    .withUserData((Base64.getEncoder().encodeToString((getUserDataScript()).getBytes())))
                    .withMonitoring(true);
            ec2Client.runInstances(runRequest);
        }
        initWorkerMessagesHandlerThreads();
    }
    private String getUserDataScript(){
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("#!/bin/bash");
        lines.add("rm Worker.jar");
        lines.add("echo Deleted Worker.jar");
        lines.add("curl -L https://github.com/Asif857/Worker/blob/master/out/artifacts/Worker_jar/Worker.jar?raw=true -o Worker.jar");
        lines.add("echo Downloading Worker.jar");
        lines.add("zip -d Worker.jar 'META-INF/.SF' 'META-INF/.RSA' 'META-INF/*SF'");
        lines.add("echo Deleting Security Issues");
        lines.add("java -jar Worker.jar");
        lines.add("echo Running Worker.jar");
        String temp = (join(lines, "\n"));
        System.out.println(temp);
        String str = Base64.getEncoder().encodeToString((join(lines, "\n").getBytes()));
        System.out.println(str);
        return str;
    }

    private static String join(Collection<String> s, String delimiter) {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iter = s.iterator();
        while (iter.hasNext()) {
            builder.append(iter.next());
            if (!iter.hasNext()) {
                break;
            }
            builder.append(delimiter);
        }
        return builder.toString();
    }

    public void initWorkerMessagesHandlerThreads(){
        int currNumOfWorkerThreads = manager.getThreadList().size() - 2;
        double currWorkersNum = countWorkers();
        double workerToThreadRatio = manager.getWorkerToThreadRatio();
        int neededThreads = (int) Math.ceil(currWorkersNum/workerToThreadRatio);
        int threadsToInit = neededThreads - currNumOfWorkerThreads;
        List<Thread> threadsList= manager.getThreadList();
        for (int i = 0; i < threadsToInit ; i++){
            Thread t = new Thread(new workerMessagesHandler(manager));
            threadsList.add(t);
            t.start();
        }
    }
    private int countWorkers(){
        int currentWorkers = 0;
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult response = ec2Client.describeInstances(request);
        boolean done = false;
        while (!done) {
            List<Reservation> reserveList = response.getReservations();
            for (Reservation reservation : reserveList) {
                for(Instance instance: reservation.getInstances()){
                    currentWorkers++;
                }
            }
            request.setNextToken(response.getNextToken());
            if (response.getNextToken() == null){
                done = true;
            }
        }
        return currentWorkers;
    }
    public void insertToFilesToSplitDeque(List<Message> messages){
        String home = System.getProperty("user.home");
        BlockingDeque filesToSplit = manager.getFilesToSplitDeque();
        for (Message message : messages) {
            String id = message.getMessageAttributes().get("id").getStringValue();
            File outputFile = new File (home + "/IdeaProjects/Manager/src/main/java/Output/" + id + ".txt");
            filesToSplit.add(outputFile);
        }
    }
    public void deleteMessagesFromToManagerSQS(List<Message> messages){
        for (Message message:messages) {
            sqsClient.deleteMessage(sqsToManagerUrl, message.getReceiptHandle());
        }
    }
}
