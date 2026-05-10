import boto3
from botocore.config import Config
import os

s3 = boto3.client('s3',
    endpoint_url='http://192.168.20.125:9000',
    aws_access_key_id='XSnCKcUT4Z5M2BbYIRQP',
    aws_secret_access_key='UbeHwkVka2CGm0mvp51ADYIKppkr9GS2gadE8EFf',
    config=Config(signature_version='s3v4'),
    region_name='us-east-1'
)

path = r'D:\code\ai_project\javaclawbot\installer\runtime\jdk-17-jre-x64.zip'
key = 'releases/2.3.0/windows/jdk-17-jre-x64.zip'
size_mb = os.path.getsize(path) / (1024*1024)
print(f'Uploading jdk-17-jre-x64.zip ({size_mb:.1f} MB) -> s3://agent/{key} ...')
s3.upload_file(path, 'agent', key)
print('Done!')
