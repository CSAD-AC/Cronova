from time import sleep

print("Hello World")

sum = 0
for i in range(1, 10):
    print(f"After {sum} times")
    sum += i
    sleep(i)

