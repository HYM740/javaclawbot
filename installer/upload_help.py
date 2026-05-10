import boto3
from botocore.config import Config
import os, glob

s3 = boto3.client('s3',
    endpoint_url='http://192.168.20.125:9000',
    aws_access_key_id='XSnCKcUT4Z5M2BbYIRQP',
    aws_secret_access_key='UbeHwkVka2CGm0mvp51ADYIKppkr9GS2gadE8EFf',
    config=Config(signature_version='s3v4'),
    region_name='us-east-1'
)

base = r'D:\code\ai_project\javaclawbot\asset'

# Upload HTML
html_path = os.path.join(base, 'NexusAi操作配置指南.html')
html_key = 'releases/help/NexusAi操作配置指南.html'
print(f'Uploading HTML...')
s3.upload_file(html_path, 'agent', html_key, ExtraArgs={'ContentType': 'text/html; charset=utf-8'})
print('  Done.')

# Upload all images
for img in glob.glob(os.path.join(base, 'images', '*.png')):
    img_name = os.path.basename(img)
    img_key = f'releases/help/images/{img_name}'
    size_kb = os.path.getsize(img) / 1024
    print(f'Uploading {img_name} ({size_kb:.0f} KB)...')
    s3.upload_file(img, 'agent', img_key, ExtraArgs={'ContentType': 'image/png'})
print('\nAll help files uploaded!')
