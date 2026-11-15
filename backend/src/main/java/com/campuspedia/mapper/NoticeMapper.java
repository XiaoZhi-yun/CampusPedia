package com.campuspedia.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campuspedia.entity.Notice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {

    @Select("SELECT COALESCE(MAX(id), 0) FROM t_notice")
    Long selectMaxId();

    @Update("ALTER TABLE t_notice AUTO_INCREMENT = #{n}")
    void resetAutoIncrement(@Param("n") long n);
}
