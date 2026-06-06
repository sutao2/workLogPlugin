#!/bin/bash

# Git 功能测试脚本
# 用于测试 GitService 的 Git 提交记录获取功能

echo "================================"
echo "Git 功能测试"
echo "================================"
echo ""

# 检查是否提供了测试日期
TEST_DATE=${1:-"2025-12-16"}

echo "测试日期: $TEST_DATE"
echo ""

# 编译测试类
echo "正在编译测试类..."
./gradlew compileTestKotlin

if [ $? -ne 0 ]; then
    echo "编译失败"
    exit 1
fi

echo "编译成功"
echo ""

# 运行测试
echo "运行测试..."
echo ""

kotlin -cp "build/classes/kotlin/test:build/classes/kotlin/main" \
    com.worklog.services.GitServiceTest "$TEST_DATE"
