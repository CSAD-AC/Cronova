import importlib.metadata
from typing import List, Dict


class EnvUtils:
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