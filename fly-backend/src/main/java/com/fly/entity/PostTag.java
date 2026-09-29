package com.fly.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** 文章-标签中间表 */
@Entity
@Table(name = "post_tag")
@IdClass(PostTag.PostTagId.class)
public class PostTag {

    @Id
    public Long postId;
    @Id
    public Long tagId;

    public PostTag() {
    }

    public PostTag(Long postId, Long tagId) {
        this.postId = postId;
        this.tagId = tagId;
    }

    public static class PostTagId implements Serializable {
        public Long postId;
        public Long tagId;

        public PostTagId() {
        }

        public PostTagId(Long postId, Long tagId) {
            this.postId = postId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PostTagId other)) {
                return false;
            }
            return Objects.equals(postId, other.postId) && Objects.equals(tagId, other.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, tagId);
        }
    }
}
