import importlib.metadata
from typing import List, Dict, Optional


class EnvUtils:
    @staticmethod
    def get_all_installed_packages() -> List[Dict[str, str]]:
        """获取所有已安装的包及其版本信息"""
        packages_info = []
        try:
            installed_packages = importlib.metadata.distributions()
            for pkg in installed_packages:
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
        """获取指定包的版本信息"""
        packages_info = []
        for pkg_name in target_packages:
            try:
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
        """检查指定包版本是否满足要求"""
        try:
            current_version = importlib.metadata.version(package_name)
            
            if not required_version or not operator:
                return True, current_version
            
            if EnvUtils._compare_versions(current_version, required_version, operator):
                return True, current_version
            else:
                return False, current_version
                
        except importlib.metadata.PackageNotFoundError:
            return False, "未安装"

    @staticmethod
    def _compare_versions(current: str, required: str, operator: str) -> bool:
        """按操作符比较版本号"""
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