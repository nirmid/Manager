package org.example;

/**
 * Hello world!
 *
 */
public class Main
{
    public static void main( String[] args )
    {
        ManagerClass manager = new ManagerClass();
        S3DownloaderAndWorkerInitiliazer T1 = new S3DownloaderAndWorkerInitiliazer(manager);
    }
}
