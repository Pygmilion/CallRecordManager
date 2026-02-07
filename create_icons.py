#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageFont
import os

# 定义不同密度的图标尺寸
sizes = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192
}

base_path = '/Users/natsusakai/Documents/CallRecordManager/app/src/main/res'

# 创建图标
for density, size in sizes.items():
    # 创建圆形图标
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # 绘制绿色圆形背景
    draw.ellipse([0, 0, size-1, size-1], fill='#3DDC84', outline='#2CA56C', width=2)
    
    # 绘制白色电话图标（简化版）
    center = size // 2
    phone_size = size // 3
    draw.ellipse([center-phone_size//2, center-phone_size//2, 
                  center+phone_size//2, center+phone_size//2], 
                 fill='#FFFFFF')
    
    # 保存 ic_launcher
    output_dir = f'{base_path}/mipmap-{density}'
    os.makedirs(output_dir, exist_ok=True)
    img.save(f'{output_dir}/ic_launcher.png')
    
    # 保存 ic_launcher_round (相同图标)
    img.save(f'{output_dir}/ic_launcher_round.png')
    
    print(f'Created icons for {density}: {size}x{size}')

print('All icons created successfully!')
