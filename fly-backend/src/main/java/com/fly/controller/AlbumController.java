package com.fly.controller;

import com.fly.common.ApiError;
import com.fly.dto.Requests.AlbumCreateReq;
import com.fly.dto.Requests.AlbumUpdateReq;
import com.fly.dto.Requests.PhotoCreateReq;
import com.fly.entity.Album;
import com.fly.entity.Photo;
import com.fly.repository.AlbumRepository;
import com.fly.repository.PhotoRepository;
import com.fly.security.RequiresAuth;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 相册（对应原 app/api/albums.py） */
@RestController
@RequestMapping("/api/albums")
public class AlbumController {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;

    public AlbumController(AlbumRepository albumRepository, PhotoRepository photoRepository) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
    }

    @GetMapping
    public List<Album> list() {
        return albumRepository.findAll(Sort.by(Sort.Direction.ASC, "sort"));
    }

    @GetMapping("/{albumId:\\d+}")
    public Album get(@PathVariable Long albumId) {
        return albumRepository.findById(albumId)
                .orElseThrow(() -> ApiError.notFound("相册不存在"));
    }

    @GetMapping("/{albumId:\\d+}/photos")
    public List<Photo> photos(@PathVariable Long albumId) {
        return photoRepository.findByAlbumIdOrderBySortAsc(albumId);
    }

    @PostMapping
    @RequiresAuth
    public Album create(@RequestBody AlbumCreateReq data) {
        if (data.title == null) {
            throw new ApiError(422, "title 为必填字段");
        }
        Album album = new Album();
        album.title = data.title;
        album.description = data.description == null ? "" : data.description;
        album.cover = data.cover == null ? "" : data.cover;
        album.sort = data.sort;
        return albumRepository.save(album);
    }

    @PutMapping("/{albumId}")
    @RequiresAuth
    public Album update(@PathVariable Long albumId, @RequestBody AlbumUpdateReq data) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> ApiError.notFound("相册不存在"));
        if (data.title != null) {
            album.title = data.title;
        }
        if (data.description != null) {
            album.description = data.description;
        }
        if (data.cover != null) {
            album.cover = data.cover;
        }
        if (data.sort != null) {
            album.sort = data.sort;
        }
        album.updatedAt = LocalDateTime.now();
        return albumRepository.save(album);
    }

    @DeleteMapping("/{albumId}")
    @RequiresAuth
    @Transactional
    public Map<String, Object> delete(@PathVariable Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> ApiError.notFound("相册不存在"));
        photoRepository.deleteByAlbumId(albumId);
        albumRepository.delete(album);
        return Map.of("ok", true);
    }

    @PostMapping("/photos")
    @RequiresAuth
    @Transactional
    public Photo addPhoto(@RequestBody PhotoCreateReq data) {
        if (data.albumId == null || data.url == null) {
            throw new ApiError(422, "album_id 和 url 为必填字段");
        }
        Photo photo = new Photo();
        photo.albumId = data.albumId;
        photo.url = data.url;
        photo.caption = data.caption == null ? "" : data.caption;
        photo.orientation = data.orientation == null ? "landscape" : data.orientation;
        photo.sort = data.sort;
        photo = photoRepository.save(photo);

        albumRepository.findById(data.albumId).ifPresent(album -> {
            album.photoCount = (int) photoRepository.countByAlbumId(data.albumId);
            album.updatedAt = LocalDateTime.now();
            albumRepository.save(album);
        });
        return photo;
    }

    @DeleteMapping("/photos/{photoId}")
    @RequiresAuth
    @Transactional
    public Map<String, Object> deletePhoto(@PathVariable Long photoId) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> ApiError.notFound("照片不存在"));
        Long albumId = photo.albumId;
        photoRepository.delete(photo);
        photoRepository.flush();

        albumRepository.findById(albumId).ifPresent(album -> {
            album.photoCount = (int) photoRepository.countByAlbumId(albumId);
            album.updatedAt = LocalDateTime.now();
            albumRepository.save(album);
        });
        return Map.of("ok", true);
    }
}
