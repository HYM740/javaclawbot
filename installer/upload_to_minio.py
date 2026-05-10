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

base = r'D:\code\ai_project\javaclawbot\installer\runtime'
files = {
    'PortableGit-2.47.0-64-bit.7z.exe': 'releases/2.3.0/windows/PortableGit-2.47.0-64-bit.7z.exe',
    'python-3.12.4-embed-amd64.zip': 'releases/2.3.0/windows/python-3.12.4-embed-amd64.zip',
    'node-v22.12.0-win-x64.zip': 'releases/2.3.0/windows/node-v22.12.0-win-x64.zip',
}

for local, remote in files.items():
    path = os.path.join(base, local)
    size_mb = os.path.getsize(path) / (1024*1024)
    print(f'Uploading {local} ({size_mb:.1f} MB) -> s3://agent/{remote} ...')
    s3.upload_file(path, 'agent', remote)
    print(f'  Done.')

print('\nAll files uploaded successfully!')

# Verify
print('\nVerifying uploaded files:')
resp = s3.list_objects_v2(Bucket='agent', Prefix='releases/2.3.0/windows/')
for obj in resp.get('Contents', []):
    print(f'  {obj["Key"]} ({obj["Size"]} bytes)')
