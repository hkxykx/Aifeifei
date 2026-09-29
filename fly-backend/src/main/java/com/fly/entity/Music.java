package com.fly.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 本地音乐（管理后台上传，前端播放器播放） */
@Entity
@Table(name = "music")
public class Music {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String title;
    public String artist = "未知歌手";
    public String album = "";
    public String cover = "";
    /** 播放地址：目录音乐为 /api/music/stream?path=...，上传的为 /uploads/... */
    public String url;
    /** 源文件绝对路径（扫描导入的目录音乐） */
    public String path;
    /** 来源：dir=本地目录导入，upload=后台上传 */
    public String source = "upload";
    /** LRC 歌词文本（从网易云/QQ音乐匹配缓存，不写入本地文件） */
    public String lrc = "";
    public double duration = 0;
    public LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
