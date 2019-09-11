//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
// trace_message.cpp: SpxTraceMessage() implementation definition
//

#define _CRT_SECURE_NO_WARNINGS

#include "stdafx.h"
#include "trace_message.h"
#include "file_utils.h"
#include "file_logger.h"
#include <chrono>
#include <stdio.h>
#include <stdarg.h>
#include <sstream>

// Note: in case of android, log to logcat
#if defined(ANDROID) || defined(__ANDROID__)
#include <android/log.h>
#endif

#define SPX_CONFIG_INCLUDE_TRACE_THREAD_ID      1
#define SPX_CONFIG_INCLUDE_TRACE_HIRES_CLOCK    1
// #define SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER   1

#ifdef SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER
#include <windows.h>
#endif // SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER

decltype(std::chrono::high_resolution_clock::now()) __g_spx_trace_message_time0 = std::chrono::high_resolution_clock::now();

void SpxTraceMessage_Internal(int level, const char* pszTitle, const char* fileName, const int lineNumber, const char* pszFormat, va_list argptr, bool logToConsole)
{
    bool logToFile = FileLogger::Instance().IsFileLoggingEnabled();
    if (!logToConsole && !logToFile)
    {
        return;
    }

    UNUSED(level);

    std::string format;
    if (SPX_CONFIG_INCLUDE_TRACE_THREAD_ID)
    {
        auto threadHash = std::hash<std::thread::id>{}(std::this_thread::get_id());
        format += "(" + std::to_string(threadHash % 1000) + "): ";
    }

    if (SPX_CONFIG_INCLUDE_TRACE_HIRES_CLOCK)
    {
        auto now = std::chrono::high_resolution_clock::now();
        unsigned long delta = (unsigned long)std::chrono::duration_cast<std::chrono::milliseconds>(now - __g_spx_trace_message_time0).count();
        format += std::to_string(delta) + "ms ";
    }

    while (*pszFormat == '\n' || *pszFormat == '\r')
    {
        if (*pszFormat == '\r')
        {
            pszTitle = nullptr;
        }

        format += *pszFormat++;
    }

    if (pszTitle != nullptr)
    {
        format += pszTitle;
    }

    std::string fileNameOnly(fileName);
    std::replace(fileNameOnly.begin(), fileNameOnly.end(), '\\', '/');

    std::ostringstream fileNameLineNumber;
    fileNameLineNumber << " " << fileNameOnly.substr(fileNameOnly.find_last_of('/', std::string::npos) + 1) << ":" << lineNumber << " ";

    format += fileNameLineNumber.str();

    format += pszFormat;
    if (format.length() < 1 || format[format.length() - 1] != '\n')
    {
        format += "\n";
    }

#ifdef SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER
    va_list argptrDbg;
#ifdef _MSC_VER
    // Avoid warnings:
    // Warning C4701: potentially uninitialized local variable 'X' used
    // Warning C4703: potentially uninitialized local pointer variable 'X' used
    argptrDbg = nullptr;
#endif
    va_copy(argptrDbg, argptr);
    char sz[4096];
    vsprintf_s(sz, 4096, format.c_str(), argptrDbg);
    OutputDebugStringA(sz);
#endif // SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER

    va_list argptrConsole;
#ifdef _MSC_VER
    // Avoid warnings:
    // Warning C4701: potentially uninitialized local variable 'X' used
    // Warning C4703: potentially uninitialized local pointer variable 'X' used
    argptrConsole = nullptr;
#endif

    if (logToConsole)
    {
        va_copy(argptrConsole, argptr);
    }

#if defined(ANDROID) || defined(__ANDROID__)
    int androidPrio = ANDROID_LOG_ERROR;
    switch (level)
    {
    case __SPX_TRACE_LEVEL_INFO:    androidPrio = ANDROID_LOG_INFO;     break; // Trace_Info
    case __SPX_TRACE_LEVEL_WARNING: androidPrio = ANDROID_LOG_WARN;     break; // Trace_Warning
    case __SPX_TRACE_LEVEL_ERROR:   androidPrio = ANDROID_LOG_ERROR;    break; // Trace_Error
    case __SPX_TRACE_LEVEL_VERBOSE: androidPrio = ANDROID_LOG_VERBOSE;  break; // Trace_Verbose
    default: androidPrio = ANDROID_LOG_FATAL; break;
    }

    if (logToConsole)
    {
        __android_log_vprint(androidPrio, "SpeechSDK", format.c_str(), argptrConsole);
    }
    if (logToFile)
    {
        FileLogger::Instance().LogToFile(std::move(format), argptr);
    }
#else
    if (logToConsole)
    {
        vfprintf(stderr, format.c_str(), argptrConsole);
    }
    if (logToFile)
    {
        FileLogger::Instance().LogToFile(std::move(format), argptr);
    }
#endif

    if (logToConsole)
    {
        va_end(argptrConsole);
    }

#ifdef SPX_CONFIG_INCLUDE_TRACE_WINDOWS_DEBUGGER
    va_end(argptrDbg);
#endif
}

void SpxTraceMessage(int level, const char* pszTitle, bool enableDebugOutput, const char* fileName, const int lineNumber, const char* pszFormat, ...)
{
    UNUSED(level);
    try
    {
        va_list argptr;
        va_start(argptr, pszFormat);
        SpxTraceMessage_Internal(level, pszTitle, fileName, lineNumber, pszFormat, argptr, enableDebugOutput);
        va_end(argptr);
    }
    catch(...)
    {
    }
}

void SpxConsoleLogger_Log(LOG_CATEGORY log_category, const char* file, const char* func, int line, unsigned int options, const char* format, ...)
{
    UNUSED(options);

    va_list args;
    va_start(args, format);

    bool enable_console_log = false;
#ifdef _DEBUG
    enable_console_log = true;
#endif

    switch (log_category)
    {
    case AZ_LOG_INFO:
        SpxTraceMessage_Internal(__SPX_TRACE_LEVEL_INFO, "SPX_TRACE_INFO: AZ_LOG_INFO: ", file, line, format, args, enable_console_log);
        break;

    case AZ_LOG_ERROR:
        SpxTraceMessage_Internal(__SPX_TRACE_LEVEL_INFO, "SPX_TRACE_ERROR: AZ_LOG_ERROR: ", file, line, format, args, enable_console_log);
        SPX_TRACE_ERROR("Error: File:%s Func:%s Line:%d ", file, func, line);
        break;

    default:
        break;
    }

    va_end(args);
}

void FileLogger::SetFilename(std::string&& name)
{
    std::lock_guard<std::mutex> lock(mtx);
    // if user tries to change a filename, throw error
    if (!filename.empty() && name != filename)
    {
        SPX_THROW_HR(SPXERR_ALREADY_INITIALIZED);
    }
    // if user sets the same filename again, do nothing
    if (file != nullptr)
    {
        return;
    }

    errno_t err = PAL::fopen_s(&file, name.c_str(), "w");
    SPX_IFFALSE_THROW_HR(err == 0, SPXERR_FILE_OPEN_FAILED);
    filename = name;
}

std::string FileLogger::GetFilename()
{
    return filename;
}

bool FileLogger::IsFileLoggingEnabled()
{
    return file != nullptr;
}

void FileLogger::CloseFile()
{
    if (file != nullptr)
    {
        fclose(file);
        file = nullptr;
    }
}

void FileLogger::LogToFile(std::string&& format, va_list argptr)
{
    if (file != nullptr)
    {
        vfprintf(file, format.c_str(), argptr);
        fflush(file);
    }
}

FileLogger& FileLogger::Instance()
{
    static FileLogger instance;
    return instance;
}