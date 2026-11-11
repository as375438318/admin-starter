package com.adminstarter.system.infrastructure.persistence.converter;

import com.adminstarter.system.domain.model.Post;
import com.adminstarter.system.infrastructure.persistence.entity.PostDO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PostConverter extends TimeConverter {

    Post toDomain(PostDO source);

    PostDO toDO(Post source);
}
