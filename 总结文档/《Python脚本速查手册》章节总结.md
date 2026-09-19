# 《Python脚本速查手册》章节总结

## 第1章：前言

### 1. 核心论点

- **问题**：为什么运维工程师需要掌握Python？
- **观点**：Python是运维工程师的必备利器，相比于Bash，它拥有更完善的生态和支持，能帮助工程师更简单地完成复杂功能，并与Ansible、SaltStack等主流运维工具无缝结合。

### 2. 关键概念/事件

- **手册结构**：分为三部分：**Click语法速查**（编写CLI工具）、**Python常用运维模块**（OS, Shutil, Sys, Datetime, psutil）、**实用Python脚本案例**（查看CPU/内存信息、统计Nginx日志IP并绘图）。


## 第2章：Click语法速查

### 1. 核心论点

- **问题**：如何使用Click库快速构建Python命令行工具（CLI）？
- **观点**：Click提供了丰富的参数类型（str, int, bool, File, Path, Choice, IntRange, DateTime）、多种选项类型（必选、多参数、Flag、密码、提问）和Argument，以及特殊功能（彩色输出、分页、进度条、清屏），是替代argparse的更优雅的方案。

### 2. 关键概念/事件

- **Param类型**：
  - `click.File`：自动以文本形式读取文件内容。
  - `click.Path`：处理文件/目录路径，可检查是否存在、是否可读写。
  - `click.Choice`：限定输入值必须在给定选择列表中。
  - `click.IntRange` / `click.FloatRange`：限定数值范围。
- **Option类型**：
  - 必选option：`required=True`
  - 多参数option：`nargs=2`
  - Flag型option：`is_flag=True`
  - 密码型option：`click.password_option()`
- **特殊用法**：
  - 彩色输出：`click.echo(click.style('text', fg='green'))`
  - 分页输出：`click.echo_via_pager()`
  - 进度条：`with click.progressbar(iterable) as bar:`

### 3. 逻辑推演/叙事脉络

本章以代码片段和注释的形式，直接展示了Click的核心用法。先按类别列出可用的Param类型及其代码示例。然后展示不同类型的Option（必选、多参数、Flag、密码、提问）。接着展示普通Argument、文件Argument、路径Argument的用法。最后汇总了特殊用法：标准输出、彩色输出、分页、清屏、进度条。整体上是速查手册风格，便于快速查找。

### 4. 经典金句/数据

> **代码示例**：

```python
@click.command()
@click.option('--count', type=click.IntRange(0, 20, clamp=True))
def repeat(count):
    click.echo(str('x') * count)
```

## 第3章：Python常用运维模块

### 1. 核心论点

- **问题**：编写Python运维脚本时，最常用、最核心的内置和第三方模块有哪些？它们的基本用法是什么？
- **观点**：`os`和`shutil`模块提供了文件和目录操作的核心功能；`sys`模块用于与Python解释器交互；`datetime`处理时间；`psutil`（第三方）是获取系统信息（CPU、内存、磁盘、网络、进程）的利器。

### 2. 关键概念/事件

- **os模块**：`os.getcwd()`, `os.chdir()`, `os.makedirs()`, `os.listdir()`, `os.remove()`, `os.rename()`, `os.environ`, `os.chmod()`等。
- **shutil模块**：`shutil.copyfile()`, `shutil.copytree()`, `shutil.move()`, `shutil.disk_usage()`。
- **sys模块**：`sys.exit()`, `sys.version`, `sys.path`, `sys.platform`, `sys.stdin/stdout/stderr`。
- **datetime模块**：`datetime.date.today()`, `datetime.now()`, `datetime.timestamp()`, `strftime()`。
- **psutil模块**（需安装）：`psutil.cpu_count()`, `psutil.cpu_times()`, `psutil.virtual_memory()`, `psutil.disk_usage()`, `psutil.net_io_counters()`, `psutil.pids()`。

### 3. 逻辑推演/叙事脉络

本章按模块组织，每个模块下列出最常用的函数/方法，并附有简短的功能说明。没有深入的解释和复杂的逻辑，而是直接给出函数名称和用途，便于运维人员在编写脚本时快速查阅和回忆。最后给出一个查看CPU、内存信息的完整脚本示例。

### 4. 经典金句/数据

> **安装命令**：`pip install psutil`

## 第4章：实用Python脚本案例

### 1. 核心论点

- **问题**：如何将前面学到的模块应用到实际的运维任务中？
- **观点**：通过两个完整示例——查看CPU、内存信息（使用psutil）和统计Nginx日志中的IP并绘制成图形（使用matplotlib），展示了从编写、执行到输出的完整流程。

### 2. 关键概念/事件

- **查看CPU、内存信息**：脚本使用`psutil.virtual_memory()`获取内存总量、已用量并计算百分比；使用`psutil.disk_usage('/')`获取磁盘信息并转换为GB。
- **统计Nginx日志IP并绘图**：
  - 读取Nginx日志文件，按行分割，提取每行的第一个字段（客户端IP）。
  - 使用字典统计每个IP的访问次数。
  - 按访问次数排序，取前10名。
  - 使用`matplotlib.pyplot`绘制柱状图，设置标题、坐标轴标签、旋转X轴标签、在柱顶显示数值。

### 3. 逻辑推演/叙事脉络

本章直接给出两个脚本的完整代码和注释。第一个脚本定义了两个函数`memissue()`和`cuplist()`，分别输出内存和磁盘信息，并在主程序调用。第二个脚本演示了日志文件处理、IP统计、排序、数据可视化（柱状图）的全过程，包含必要的导入语句和绘图细节。

### 4. 经典金句/数据

> **matplotlib绘图关键代码**：

```python
plt.bar(x, y)
plt.xticks(rotation=70)
for a,b in zip(x,y):
    plt.text(a,b,'%.0f'% b, ha='center', va='bottom', fontsize=7)
```

---

# 