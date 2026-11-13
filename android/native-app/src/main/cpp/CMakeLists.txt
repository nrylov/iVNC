# Build only pinned LibVNCClient; Apple authentication uses Android's crypto provider.
cmake_minimum_required(VERSION 3.18)
project(ivnc_native C CXX)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WITH_LIBVNCCLIENT ON CACHE BOOL "" FORCE)
set(WITH_LIBVNCSERVER OFF CACHE BOOL "" FORCE)
foreach(feature LZO JPEG PNG SDL GTK LIBSSHTUNNEL GNUTLS OPENSSL SYSTEMD GCRYPT FFMPEG TIGHTVNC_FILETRANSFER WEBSOCKETS SASL XCB EXAMPLES TESTS QT)
    set(WITH_${feature} OFF CACHE BOOL "" FORCE)
endforeach()
set(WITH_ZLIB ON CACHE BOOL "" FORCE)
get_filename_component(IVNC_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
add_subdirectory("${IVNC_ROOT}/.dev/libvncclient-source" libvnc)
# AGP's Debug build otherwise leaves the C framebuffer decoders unoptimized;
# cppFlags applies only to our C++ files. Keep symbols/UI debugging, optimize
# the actual native engine so performance measurements are meaningful.
target_compile_options(vncclient PRIVATE -O3)
add_library(ivncrfb SHARED native-session.cpp native-jni.cpp apple-auth.cpp apple-transport.cpp apple-session.cpp apple-display.cpp apple-keyboard.cpp apple-clipboard.cpp apple-hp.cpp)
target_include_directories(ivncrfb PRIVATE "${IVNC_ROOT}/.dev/libvncclient-source/include" "${CMAKE_CURRENT_BINARY_DIR}/libvnc/include")
target_link_libraries(ivncrfb vncclient android log)
target_link_options(ivncrfb PRIVATE "-Wl,-z,max-page-size=16384")
