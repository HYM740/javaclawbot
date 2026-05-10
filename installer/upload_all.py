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

# Upload NexusAI.jar
jar_path = r'D:\code\ai_project\javaclawbot\target\NexusAI.jar'
jar_key = 'releases/2.3.0/windows/NexusAI.jar'
jar_size = os.path.getsize(jar_path) / (1024*1024)
print(f'Uploading NexusAI.jar ({jar_size:.1f} MB) -> s3://agent/{jar_key} ...')
s3.upload_file(jar_path, 'agent', jar_key)
print('  Done.')

# Upload macOS/Linux Node.js
runtime_base = r'D:\code\ai_project\javaclawbot\installer\runtime'
platform_files = {
    'node-v22.12.0-darwin-arm64.tar.gz': 'releases/2.3.0/macos-arm64/node-macos-arm64.tar.gz',
    'node-v22.12.0-darwin-x64.tar.gz': 'releases/2.3.0/macos-x64/node-macos-x64.tar.gz',
    'node-v22.12.0-linux-x64.tar.xz': 'releases/2.3.0/linux-x64/node-linux-x64.tar.xz',
}

for local, remote in platform_files.items():
    path = os.path.join(runtime_base, local)
    if os.path.exists(path):
        size_mb = os.path.getsize(path) / (1024*1024)
        print(f'Uploading {local} ({size_mb:.1f} MB) -> s3://agent/{remote} ...')
        s3.upload_file(path, 'agent', remote)
        print('  Done.')
    else:
        print(f'SKIP: {local} not found')

print('\nAll uploads complete!')

# Final listing
print('\n=== MinIO files under releases/2.3.0/ ===')
resp = s3.list_objects_v2(Bucket='agent', Prefix='releases/2.3.0/')
for obj in resp.get('Contents', []):
    size_mb = obj['Size'] / (1024*1024)
    print(f'  {obj["Key"]} ({size_mb:.1f} MB)')
