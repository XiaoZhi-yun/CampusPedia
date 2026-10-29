package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {}
