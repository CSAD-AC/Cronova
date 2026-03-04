# -*- coding: utf-8 -*-
"""
测试项目主入口文件
用于测试文件夹任务执行时的完整性和文件读取能力
"""
import os
import sys
import json
import xml.etree.ElementTree as ET

# 获取当前目录路径
BASE_DIR = os.path.dirname(os.path.abspath(__file__))


def read_config():
    """读取配置文件"""
    config_path = os.path.join(BASE_DIR, 'config.properties')
    config = {}
    if os.path.exists(config_path):
        with open(config_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    key, value = line.split('=', 1)
                    config[key.strip()] = value.strip()
    return config


def read_pom_xml():
    """读取 Maven pom.xml 文件"""
    pom_path = os.path.join(BASE_DIR, 'pom.xml')
    if os.path.exists(pom_path):
        try:
            tree = ET.parse(pom_path)
            root = tree.getroot()
            # 处理命名空间 - 尝试获取项目名和版本
            name = root.find('.//{http://maven.apache.org/POM/4.0.0}name') or root.find('.//name')
            version = root.find('.//{http://maven.apache.org/POM/4.0.0}version') or root.find('.//version')
            return {
                'name': name.text if name is not None else 'N/A',
                'version': version.text if version is not None else 'N/A'
            }
        except Exception as e:
            return {'error': str(e)}
    return {}


def read_package_json():
    """读取 Node.js package.json 文件"""
    pkg_path = os.path.join(BASE_DIR, 'package.json')
    if os.path.exists(pkg_path):
        with open(pkg_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def read_requirements_txt():
    """读取 Python requirements.txt 文件"""
    req_path = os.path.join(BASE_DIR, 'requirements.txt')
    deps = []
    if os.path.exists(req_path):
        with open(req_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    deps.append(line)
    return deps


def read_test_data():
    """读取测试数据 JSON 文件"""
    data_path = os.path.join(BASE_DIR, 'tests', 'test_data.json')
    if os.path.exists(data_path):
        with open(data_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    return {}


def list_all_files():
    """列出所有文件并按后缀分类"""
    file_types = {
        '.py': [],
        '.json': [],
        '.xml': [],
        '.txt': [],
        '.properties': [],
        '.md': [],
        'other': []
    }

    for root, dirs, files in os.walk(BASE_DIR):
        # 跳过 __pycache__ 目录
        dirs[:] = [d for d in dirs if d != '__pycache__' and not d.startswith('.')]

        for file in files:
            if file.startswith('.'):
                continue

            ext = os.path.splitext(file)[1].lower()
            rel_path = os.path.relpath(os.path.join(root, file), BASE_DIR)

            if ext in file_types:
                file_types[ext].append(rel_path)
            else:
                file_types['other'].append(rel_path)

    return file_types


def main():
    """主函数"""
    print("=" * 60)
    print("测试项目 - 文件夹任务执行测试")
    print("=" * 60)

    # 1. 列出所有文件
    print("\n[1] 扫描项目文件结构:")
    file_types = list_all_files()
    for ext, files in file_types.items():
        if files:
            print(f"\n  {ext} 文件 ({len(files)} 个):")
            for f in files:
                print(f"    - {f}")

    # 2. 读取各种配置文件
    print("\n[2] 读取配置文件:")

    print("\n  config.properties:")
    config = read_config()
    for k, v in config.items():
        print(f"    {k} = {v}")

    print("\n  pom.xml:")
    pom = read_pom_xml()
    print(f"    项目名称: {pom.get('name', 'N/A')}")
    print(f"    项目版本: {pom.get('version', 'N/A')}")

    print("\n  package.json:")
    pkg = read_package_json()
    print(f"    项目名称: {pkg.get('name', 'N/A')}")
    print(f"    项目版本: {pkg.get('version', 'N/A')}")
    if 'dependencies' in pkg:
        print(f"    依赖数量: {len(pkg['dependencies'])}")

    print("\n  requirements.txt:")
    deps = read_requirements_txt()
    for dep in deps:
        print(f"    - {dep}")

    # 3. 读取测试数据并处理
    print("\n[3] 读取并处理测试数据:")

    # 导入项目模块
    sys.path.insert(0, BASE_DIR)
    from src.services import (
        process_data, transform_data, validate_data,
        user_service, order_service, product_service
    )

    test_data = read_test_data()
    if 'users' in test_data:
        print(f"    原始用户数: {len(test_data['users'])}")

        # 处理数据
        enabled_users = process_data(test_data['users'])
        print(f"    启用的用户数: {len(enabled_users)}")

        # 转换数据
        if test_data['users']:
            transformed = transform_data(test_data['users'][0])
            print(f"    转换后的数据: {transformed}")

    # 4. 验证数据
    print("\n[4] 数据验证测试:")
    valid = validate_data({"id": 1, "name": "test"})
    print(f"    有效数据验证: {valid}")

    invalid = validate_data({"id": 1})
    print(f"    无效数据验证: {invalid}")

    # 5. 业务服务调用测试
    print("\n[5] 业务服务调用测试:")

    # 5.1 用户服务测试
    print("\n  5.1 用户服务:")
    all_users = user_service.get_all_users()
    print(f"    所有用户数: {len(all_users)}")

    user_stats = user_service.get_user_statistics(1)
    print(f"    用户1统计: 总订单={user_stats.get('total_orders', 0)}, "
          f"已完成={user_stats.get('completed_orders', 0)}, "
          f"总消费=¥{user_stats.get('total_spent', 0):.2f}")

    # 5.2 产品服务测试
    print("\n  5.2 产品服务:")
    all_products = product_service.get_all_products()
    print(f"    所有产品数: {len(all_products)}")

    products = product_service.get_products_by_category("电子产品")
    print(f"    电子产品数: {len(products)}")

    low_stock = product_service.get_low_stock_products(100)
    print(f"    库存<100的产品数: {len(low_stock)}")

    # 搜索产品
    search_results = product_service.search_products("iPhone")
    print(f"    搜索'iPhone'结果: {len(search_results)} 个")

    # 5.3 订单服务测试
    print("\n  5.3 订单服务:")
    user_orders = order_service.get_user_orders(1)
    print(f"    用户1的订单数: {len(user_orders)}")

    # 创建新订单
    try:
        new_order = order_service.create_order(2, 3, 2)
        print(f"    创建订单成功: 订单ID={new_order['order']['id']}, "
              f"金额=¥{new_order['order']['total_amount']:.2f}")
    except Exception as e:
        print(f"    创建订单: {e}")

    # 更新订单状态
    update_result = order_service.update_order_status(1, "completed")
    print(f"    更新订单状态: {update_result['message']}")

    # 订单统计
    order_stats = order_service.get_order_statistics()
    print(f"    订单统计: 总订单={order_stats['total_orders']}, "
          f"已完成={order_stats['completed_orders']}, "
          f"总收入=¥{order_stats['total_revenue']:.2f}")

    # 5.4 跨服务调用测试
    print("\n  5.4 跨服务调用（订单 -> 用户 -> 产品）:")
    all_orders = order_service.get_all_orders()
    for order in all_orders[:2]:
        user_info = user_service.get_user_by_id(order['user_id'])
        print(f"    订单{order['id']}: 用户={user_info['name'] if user_info else 'N/A'}, "
              f"产品={order['product_name']}, 金额=¥{order['total_amount']:.2f}")

    # 6. 数据库查询测试
    print("\n[6] 数据库查询测试:")

    from src.services import database_service
    from src.db_connection import db_connection

    # 6.1 连接数据库
    print("\n  6.1 数据库连接:")
    conn_result = db_connection.connect()
    print(f"    连接结果: {'成功' if conn_result else '失败'}")
    if db_connection.connection_info:
        print(f"    服务器版本: {db_connection.connection_info.get('server_version')}")
        print(f"    连接ID: {db_connection.connection_info.get('connection_id')}")

    # 6.2 查询 Worker 信息
    print("\n  6.2 查询 Worker 信息:")
    worker_stats = database_service.get_worker_stats()
    print(f"    Worker 总数: {worker_stats['total_workers']}")
    print(f"    在线 Worker: {worker_stats['online_workers']}")
    print(f"    平均 CPU 使用率: {worker_stats['avg_cpu_usage']:.2f}%")
    print(f"    平均内存使用率: {worker_stats['avg_mem_usage']:.2f}%")

    # 查询具体 Worker
    worker = database_service.get_worker_by_id('74cb00be2c93290d8eeb17eeed7f3809')
    if worker:
        print(f"    Worker 详情: host={worker['host']}, ip={worker['ip']}, status={worker['status']}")

    # 6.3 查询任务信息
    print("\n  6.3 查询任务信息:")
    task_stats = database_service.get_task_stats()
    print(f"    任务总数: {task_stats['total_tasks']}")
    print(f"    活跃任务: {task_stats['active_tasks']}")
    print(f"    脚本任务: {task_stats['script_tasks']}")
    print(f"    回调任务: {task_stats['callback_tasks']}")

    # 查询具体任务
    task = database_service.get_task_by_id('d83d5ba5716a8282d439883132a37bc3')
    if task:
        print(f"    任务详情: name={task['name']}, script_type={task.get('script_type')}, status={task['status']}")

    # 6.4 查询脚本信息
    print("\n  6.4 查询脚本信息:")
    all_scripts = database_service.get_all_scripts()
    print(f"    脚本总数: {len(all_scripts)}")

    # 查询目录脚本
    dir_scripts = database_service.get_directory_scripts()
    print(f"    目录类型脚本: {len(dir_scripts)}")
    for ds in dir_scripts:
        children = database_service.get_scripts_by_parent(ds['id'])
        print(f"    目录 '{ds['name']}' 包含 {len(children)} 个子项")

    # 6.5 查询标签信息
    print("\n  6.5 查询标签信息:")
    all_tags = database_service.get_all_tags()
    print(f"    标签总数: {len(all_tags)}")
    for tag in all_tags:
        print(f"    - {tag['name']}: {tag.get('description', '')}")

    # 6.6 查询最近任务执行记录
    print("\n  6.6 查询最近任务执行记录:")
    recent_runs = database_service.get_recent_task_runs(3)
    print(f"    最近执行记录: {len(recent_runs)} 条")
    for run in recent_runs:
        print(f"    - ID: {run['id'][:8]}..., status={run['status']}, duration={run.get('duration_ms', 'N/A')}ms")

    # 6.7 执行自定义 SQL 查询
    print("\n  6.7 执行自定义 SQL 查询:")
    # 查询表列表
    tables = database_service.execute_custom_query("SHOW TABLES")
    print(f"    数据库表数量: {len(tables)}")

    # 查询 Worker 表结构
    schema = db_connection.get_table_schema('workers')
    print(f"    Workers 表字段数: {len(schema)}")

    # 关闭连接
    db_connection.close()
    print(f"    数据库连接已关闭")

    # 7. 统计信息
    print("\n[7] 项目统计信息:")
    total_files = sum(len(files) for files in file_types.values())
    print(f"    总文件数: {total_files}")

    # 计算代码行数（仅统计 .py 文件）
    total_lines = 0
    for root, dirs, files in os.walk(BASE_DIR):
        dirs[:] = [d for d in dirs if d != '__pycache__']
        for file in files:
            if file.endswith('.py'):
                path = os.path.join(root, file)
                with open(path, 'r', encoding='utf-8') as f:
                    total_lines += len(f.readlines())
    print(f"    Python 代码总行数: {total_lines}")

    print("\n" + "=" * 60)
    print("测试完成！文件夹任务执行成功！")
    print("=" * 60)

    return 0


if __name__ == "__main__":
    sys.exit(main())
