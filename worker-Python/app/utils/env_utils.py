import importlib.metadata
from typing import List, Dict, Optional


class EnvUtils:
    @staticmethod
    def get_all_installed_packages() -> List[Dict[str, str]]:
        """
        获取所有已安装的包及其版本信息
        :return: 包含包名、版本的字典列表
        """
        packages_info = []
        try:
            # 获取所有已安装的包
            installed_packages = importlib.metadata.distributions()
            for pkg in installed_packages:
                # 获取包的元数据
                name = pkg.metadata.get('Name')
                version = pkg.version
                if name and version:
                    packages_info.append({
                        "name": name,
                        "version": version
                    })
        except Exception as e:
            print(f"获取已安装包列表失败：{str(e)}")
            # 回退到使用target_packages
            packages_info = EnvUtils.get_installed_packages([
                "numpy", "pandas", "minio", "requests", "rocketmq", "python"
            ])
        return packages_info

    @staticmethod
    def get_installed_packages(target_packages: List[str]) -> List[Dict[str, str]]:
        """
        获取指定包的版本信息
        :param target_packages: 需要检测的包名列表
        :return: 包含包名、版本、操作符的字典列表
        """
        packages_info = []
        for pkg_name in target_packages:
            try:
                # 获取包版本
                version = importlib.metadata.version(pkg_name)
                packages_info.append({
                    "name": pkg_name,
                    "version": version
                })
            except importlib.metadata.PackageNotFoundError:
                # 包未安装时跳过或标记
                print(f"警告：包 {pkg_name} 未在当前环境中安装，跳过添加标签")
        return packages_info

    @staticmethod
    def check_package_version(package_name: str, required_version: Optional[str] = None, 
                             operator: Optional[str] = None) -> tuple[bool, str]:
        """
        检查指定包的版本是否满足要求
        :param package_name: 包名
        :param required_version: 要求的版本
        :param operator: 版本比较操作符（如 >=, ==, <=, >, <）
        :return: (是否满足要求, 当前版本信息)
        """
        try:
            # 获取当前版本
            current_version = importlib.metadata.version(package_name)
            
            if not required_version or not operator:
                return True, current_version
            
            # 比较版本
            if EnvUtils._compare_versions(current_version, required_version, operator):
                return True, current_version
            else:
                return False, current_version
                
        except importlib.metadata.PackageNotFoundError:
            return False, "未安装"

    @staticmethod
    def _compare_versions(current: str, required: str, operator: str) -> bool:
        """
        比较版本号
        :param current: 当前版本
        :param required: 要求版本
        :param operator: 操作符
        :return: 是否满足要求
        """
        try:
            from packaging import version
            current_v = version.parse(current)
            required_v = version.parse(required)
            
            if operator == ">=":
                return current_v >= required_v
            elif operator == "<=":
                return current_v <= required_v
            elif operator == ">":
                return current_v > required_v
            elif operator == "<":
                return current_v < required_v
            elif operator == "==":
                return current_v == required_v
            elif operator == "!=":
                return current_v != required_v
            else:
                # 默认使用 >=
                return current_v >= required_v
        except Exception as e:
            print(f"版本比较失败：{str(e)}")
            # 简单字符串比较作为后备
            return current >= required