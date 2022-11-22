package org.example;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class workerMessagesHandler implements Runnable {
    final private String workerToManagerSQS = "";
    final private AmazonSQS sqsClient;

    final private AmazonS3 s3Client;
    final private String managerToLocalApplicationSQSURL = "";
    private ConcurrentHashMap<String,File> fileIDHashmap;
    private String uploadBucket;
    final private ManagerClass manager;
    final private AmazonEC2 ec2Client;

    public workerMessagesHandler(ManagerClass manager) {
        this.ec2Client = manager.getEc2Client();
        this.manager = manager;
        this.uploadBucket = manager.getUploadBucket();
        this.s3Client = manager.getS3Client();
        this.sqsClient = manager.getSqsClient();
        this.fileIDHashmap = manager.getFileIDHashmap();
    }

    public List<Message> getMessagesFromWorkerSQS() throws InterruptedException {
        Message message = null;
        ReceiveMessageRequest request = new ReceiveMessageRequest()
                .withQueueUrl(workerToManagerSQS)
                .withMaxNumberOfMessages(10)
                .withMessageAttributeNames("All");
        List<Message> messages = null;
        while (messages == null) {
            messages = sqsClient.receiveMessage(request).getMessages();
            Thread.sleep(5000);
        }
        return messages;
    }
    public List<File> updateFiles(List<Message> messages) throws IOException {
        List<File> finishedFiles = new ArrayList<>();
        for (Message message: messages){
            String id = message.getMessageAttributes().get("id").getStringValue();
            String imageUrl = message.getBody();
            String imageText = message.getMessageAttributes().get("message").getStringValue();
            boolean eof = Boolean.getBoolean(message.getMessageAttributes().get("eof").getStringValue());
            File file = fileIDHashmap.get(id);
            writeToFile(file,imageUrl,imageText);
            if (eof){
                finishedFiles.add(file);
            }
        }
        return finishedFiles;
    }

    private void writeToFile(File file,String imageUrl,String imageText) throws IOException {
        FileWriter fw = null;
        BufferedWriter bw = null;
        PrintWriter pw = null;
        try {
            fw = new FileWriter(file, true);
            bw = new BufferedWriter(fw);
            pw = new PrintWriter(bw);
            pw.println(imageUrl);
            pw.println(imageText);
            pw.flush();
        } finally {
            try {
                fw.close();
                bw.close();
                pw.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    public void uploadToS3(List<File> files){
        for (File file : files){
            String s3OutputPath = "Output/" + file.getName();
            s3Client.putObject(uploadBucket,s3OutputPath,file);
            fileIDHashmap.remove(file.getName());
        }

    }

    public void sendOutputURLToLocalApplication(List<File> files){
        for (File file:files){
            String id = file.getName();
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put(id, new MessageAttributeValue()
                    .withStringValue("Output/" + file.getName())
                    .withDataType("String"));
            SendMessageRequest requestMessageSend = new SendMessageRequest()
                    .withQueueUrl(managerToLocalApplicationSQSURL)
                    .withMessageBody("Sht")
                    .withMessageAttributes(messageAttributes)
                    .withMessageDeduplicationId(id)
                    .withMessageGroupId(id);
            SendMessageResult result = sqsClient.sendMessage(requestMessageSend);
            System.out.println(result.getMessageId());
        }
    }

    public void shutDownWorkers(){
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult response = ec2Client.describeInstances(request);
        boolean done = false;
        while (!done) {
            List<Reservation> reserveList = response.getReservations();
            for (Reservation reservation : reserveList) {
                if (!reservation.getRequesterId().equals("manager")) {
                    for (Instance instance : reservation.getInstances()) {
                        terminateInstance(instance);
                    }
                }
            }
            request.setNextToken(response.getNextToken());
            if (response.getNextToken() == null) {
                done = true;
            }
        }
    }

    private void terminateInstance(Instance instance) {
        String instanceId = instance.getInstanceId();
        TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest()
                .withInstanceIds(instanceId);
        ec2Client.terminateInstances(terminateInstancesRequest)
                .getTerminatingInstances()
                .get(0)
                .getPreviousState()
                .getName();
        System.out.println("The Instance is terminated with id: " + instanceId);
    }


    public void run() {
        while(!manager.isTerminated() || !manager.getFileIDHashmap().isEmpty()){
            List<Message> messages = null;
            try {
                messages = getMessagesFromWorkerSQS();
                List<File> filesToUpload = updateFiles(messages);
                uploadToS3(filesToUpload);
                sendOutputURLToLocalApplication(filesToUpload);
            }catch(Exception e){
                e.printStackTrace();
            }
        }
        shutDownWorkers();


    }
}
