package com.yulin.videoRecording.controller;

import java.io.*;

/**
 * 单独录制音视频
 * ffmpeg
 */
public class Recorder {
    private ProcessBuilder processBuilder;
    private Process process;
    private BufferedWriter writer;
    private Thread outputThread;
    private boolean isRecording = false;

    // 初始化录制器
    public Recorder(String videoDevice, String audioDevice, String outputFileName) {
        String ffmpegCmd = "ffmpeg -f dshow -i video=\"" + videoDevice + "\":audio=\"" + audioDevice + "\" -c:v libx264 -preset ultrafast -c:a aac " + outputFileName;
        processBuilder = new ProcessBuilder("cmd.exe", "/c", ffmpegCmd);
        processBuilder.redirectErrorStream(true);
    }

    // 开始录制
    public void startRecording() {
        try {
            process = processBuilder.start();
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            // 读取FFmpeg输出的线程
            outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null && isRecording) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            outputThread.setDaemon(true);
            outputThread.start();
            isRecording = true;
            System.out.println("录制开始");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 结束录制
    public void stopRecording() {
        try {
            if (isRecording) {
                writer.write("q");
                writer.flush();
                writer.close();

                // 等待进程结束
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    System.out.println("录制完成");
                } else {
                    System.err.println("录制失败");
                }

                isRecording = false;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 这里使用main方法进行测试
     * @param args
     */
    public static void main(String[] args) {
        String videoDevice = "HIK 1080P Camera";
        String audioDevice = "麦克风 (HIK 1080P Camera-Audio)";
        String outputFileName = "D:\\yulin\\Downloads\\output.flv";

        Recorder recorder = new Recorder(videoDevice, audioDevice, outputFileName);

        // 开始录制
        recorder.startRecording();

        try {
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 停止录制
        recorder.stopRecording();
    }
}
