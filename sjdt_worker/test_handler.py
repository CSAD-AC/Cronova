from app.config import Config
from app.service.task_handler.python_handler import PythonHandler
from app.model.task import TaskExecuteMessage, ScriptInfo

# 1. 初始化配置
Config.load_local_config()

# 2. 手动构造任务信息
task = TaskExecuteMessage(
    taskId="test_task_001",
    taskName="测试python处理器",
    scriptType="python",
    script=ScriptInfo(
        id="script_001",
        name="hello_world.py",
        path="minio://scripts-bucket/hello_world.py",
        tags=[]
    ),
    taskType="once",
    timeoutSeconds=5
)

# 3. 调用处理器执行任务
handler = PythonHandler()
result = handler.handle(task)
print(f"任务执行结果：{'成功' if result else '失败'}")