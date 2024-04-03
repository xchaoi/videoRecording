package com.yulin.videoRecording.controller;

import com.yulin.videoRecording.doman.model.AjaxResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 双摄像头边录制边合成
 * ffmpeg
 */
@RestController
public class DualCamerasController {

    @Value("${yulin.camera1}")
    private String videoDevice1;

    @Value("${yulin.camera2}")
    private String videoDevice2;

    //    @Value("${yulin.audioDevice}")
    private final String audioDevice = "麦克风阵列 (Realtek(R) Audio)";

    @Value("${yulin.filePath}")
    private String filePath;
    private ProcessBuilder processBuilder;
    private Process process;
    private BufferedWriter writer;
    private Thread outputThread;
    private boolean isRecording = false;

    /**
     * 初始化ffmpeg
     * 这个是边录制边合成
     * 两个摄像头分辨率设置的都是640x480格式的
     * 默认为 yuyv422
     * 视频镜像已进行反转
     *
     * @return outputFileName
     */
    public String Recorder() {

        // 时间格式化
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        // 输出文件名
        String outputFileName = LocalDateTime.now().format(sdf) + ".flv";
        // 输出文件完整路径
        String outputFile = filePath + outputFileName;
        // ffmpeg命令
        String ffmpegCmd = "ffmpeg -f dshow -video_size 640x480 -i video=\"" + videoDevice1 + "\" -f dshow -video_size 640x480 -i video=\"" + videoDevice2 + "\" -f dshow -i audio=\"" + audioDevice + "\" -filter_complex \"[0:v]hflip[v0];[1:v]hflip[v1];[v0]pad=iw*2:ih[int];[int][v1]overlay=W/2:0[vid]\" -map \"[vid]\" -map 2:a -c:v libx264 -preset ultrafast -crf 23 -c:a aac -ac 2 -ar 44100" + outputFile;
        processBuilder = new ProcessBuilder("cmd.exe", "/c", ffmpegCmd);
        processBuilder.redirectErrorStream(true);
        return outputFileName;
    }

    /**
     * 开始录制
     * @return
     */
    @GetMapping("/start")
    public AjaxResult startRecording() {

        try {
            String recorder = Recorder();
            // 启动FFmpeg
            process = processBuilder.start();
            // 获取FFmpeg的输入流
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

            // 设置守护线程
            outputThread.setDaemon(true);
            // 启动线程
            outputThread.start();
            isRecording = true;
            System.out.println("录制开始");
            // 等待ffmpeg进程结束
            int exitCode = process.waitFor();

            // 读取ffmpeg错误输出流
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                StringBuilder errorMessage = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorMessage.append(line).append("\n");
                }

                if (exitCode != 0) {
                    stopRecording();
                    return AjaxResult.error("录像启动失败，请联系管理员，返回码：" + exitCode + "，错误信息：" + errorMessage);
                }
            }

            return AjaxResult.success(recorder); // 返回生成的文件名称
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
            return AjaxResult.error("无法录像：" + e.getMessage()); // 返回无法录像的原因
        }
    }

    /**
     * 停止录制
     * @return
     */
    @GetMapping("/end")
    public AjaxResult stopRecording() {
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
        return AjaxResult.success("录制已停止");
    }

    /**
     * 下载视频
     * 已设置默认下载的后缀名为 .flv
     * @return
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadFile(String outputFileName) {
        try {
            // 指定文件路径，文件存储在当前运行目录下的 files 文件夹内
            Path filePath1 = Paths.get(filePath, outputFileName);

            // 设置响应头，指定文件名和内容类型
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + outputFileName);
            // 手动设置内容类型为 FLV 视频
            headers.add(HttpHeaders.CONTENT_TYPE, "video/x-flv");

            // 读取文件内容并返回给客户端
            byte[] fileContent = Files.readAllBytes(filePath1);
            return ResponseEntity.ok().headers(headers).body(fileContent);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}
