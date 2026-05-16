import re

with open('D:/code/ai_project/javaclawbot/src/main/java/providers/ProviderRegistry.java', 'r', encoding='utf-8') as f:
    content = f.read()

# 找到DeepSeek定义结束的位置
pattern = r'(// DeepSeek.*?\.build\(\),)'
match = re.search(pattern, content, re.DOTALL)
if match:
    deepseek_def = match.group(1)
    xiaomi_def = deepseek_def + '''

            // Xiaomi MiMo
            ProviderSpec.builder("xiaomi")
                    .keywords("xiaomi", "mimo")
                    .envKey("MIMO_API_KEY")
                    .displayName("Xiaomi MiMo")
                    .litellmPrefix("")
                    .detectByBaseKeyword("xiaomimimo")
                    .defaultApiBase("https://api.xiaomimimo.com/v1")
                    .direct(true)
                    .build(),'''
    content = content.replace(deepseek_def, xiaomi_def)
    with open('D:/code/ai_project/javaclawbot/src/main/java/providers/ProviderRegistry.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print('Xiaomi provider added successfully')
else:
    print('Could not find DeepSeek definition')