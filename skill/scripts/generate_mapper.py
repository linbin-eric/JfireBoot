#!/usr/bin/env python3
"""
生成 JfireBoot Mapper 接口模板
"""

import sys
import os
from pathlib import Path


def to_camel_case(snake_str):
    """将下划线命名转换为驼峰命名"""
    components = snake_str.split('_')
    return ''.join(x.title() for x in components)


def generate_mapper(entity_name, table_name, package_name="com.example.mapper"):
    """生成 Mapper 接口代码"""
    mapper_code = f'''package {package_name};

import cc.jfire.jsql.annotation.Sql;
import cc.jfire.jsql.mapper.Repository;
import cc.jfire.starter.jsql.AutoMapper;
import com.example.entity.{entity_name};

import java.util.List;

/**
 * {entity_name} Mapper 接口
 * 自动生成的数据访问层接口
 */
@AutoMapper
public interface {entity_name}Mapper extends Repository<{entity_name}> {{

    /**
     * 根据状态查询列表
     */
    @Sql(sql = "SELECT * FROM {table_name} WHERE status = ${{status}}", paramNames = "status")
    List<{entity_name}> findByStatus(String status);

    /**
     * 统计数量
     */
    @Sql(sql = "SELECT COUNT(*) FROM {table_name}", paramNames = {{}})
    Long count();

    /**
     * 分页查询
     */
    @Sql(
        sql = "SELECT * FROM {table_name} LIMIT ${{offset}}, ${{limit}}",
        paramNames = {{"offset", "limit"}}
    )
    List<{entity_name}> findWithPage(int offset, int limit);
}}
'''
    return mapper_code


def generate_entity(entity_name, table_name, fields, package_name="com.example.entity"):
    """生成实体类代码"""
    field_declarations = []
    for field in fields:
        field_name = field['name']
        field_type = field['type']
        column_name = field.get('column', field_name)

        if field.get('is_id', False):
            field_declarations.append(f'''    @Id
    @Column("{column_name}")
    private {field_type} {field_name};
''')
        else:
            field_declarations.append(f'''    @Column("{column_name}")
    private {field_type} {field_name};
''')

    entity_code = f'''package {package_name};

import cc.jfire.jsql.annotation.Column;
import cc.jfire.jsql.annotation.Id;
import cc.jfire.jsql.annotation.Table;
import lombok.Data;

/**
 * {entity_name} 实体类
 * 映射到数据库表: {table_name}
 */
@Data
@Table("{table_name}")
public class {entity_name} {{

{''.join(field_declarations)}}}
'''
    return entity_code


def main():
    if len(sys.argv) < 3:
        print("用法: python generate_mapper.py <实体名> <表名> [包名]")
        print("示例: python generate_mapper.py User users com.example.mapper")
        sys.exit(1)

    entity_name = sys.argv[1]
    table_name = sys.argv[2]
    package_name = sys.argv[3] if len(sys.argv) > 3 else "com.example.mapper"

    # 生成 Mapper
    mapper_code = generate_mapper(entity_name, table_name, package_name)
    mapper_file = f"{entity_name}Mapper.java"

    with open(mapper_file, 'w', encoding='utf-8') as f:
        f.write(mapper_code)

    print(f"✅ Mapper 接口已生成: {mapper_file}")
    print(f"\n生成的代码:\n")
    print(mapper_code)


if __name__ == "__main__":
    main()
