# 测试项目

这是一个用于测试任务引擎文件夹执行功能的测试项目。

## 项目结构

```
test2/
├── main.py                 # 主入口文件
├── config.properties       # 配置文件
├── pom.xml                # Maven 配置（XML格式）
├── package.json           # Node.js 配置（JSON格式）
├── requirements.txt       # Python 依赖
├── README.md              # 本文件
├── src/
│   ├── __init__.py        # 包初始化
│   ├── utils.py           # 工具模块
│   └── services/
│       ├── __init__.py
│       └── data_service.py # 数据处理服务
└── tests/
    ├── test_data.json     # 测试数据
    └── test_service.py    # 单元测试
```

## 执行测试

```bash
cd test2
python main.py
```

## 测试内容

1. **文件结构扫描**: 递归扫描所有子目录和文件
2. **配置文件读取**:
   - `.properties` 格式
   - `.xml` 格式
   - `.json` 格式
   - `.txt` 格式
3. **模块导入**: 测试 Python 包导入功能
4. **数据处理**: 测试自定义模块调用
5. **代码统计**: 统计文件数量和代码行数
