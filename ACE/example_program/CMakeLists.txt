cmake_minimum_required(VERSION 3.0)
# Set the project name
project(coin_prog)

add_compile_options(-Wall -Werror)

# Add an executable
add_executable(${PROJECT_NAME} main.c)
add_executable(fake_display fake.c)
add_executable(rev_endian rev_endian.cpp)
target_link_libraries(rev_endian lib_ACE)

# ============================================= install options ========================
