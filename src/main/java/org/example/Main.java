package org.example;
public class Main
{
    public static void main( String[] args ) throws InterruptedException {
        ManagerClass manager = new ManagerClass();
        S3DownloaderAndWorkerInitiliazer s3DownloaderAndWorkerInitiliazer = new S3DownloaderAndWorkerInitiliazer(manager);
        FileSplitter fileSplitter = new FileSplitter(manager);
        workerMessagesHandler workerMessagesHandler = new workerMessagesHandler(manager);
        Thread T1 = new Thread(s3DownloaderAndWorkerInitiliazer);
        Thread T2 = new Thread(fileSplitter);
        Thread T3 = new Thread(workerMessagesHandler);
        T1.run();
        T2.run();
        T3.run();
        T1.join();
        T2.join();
        T3.join();
    }
}
