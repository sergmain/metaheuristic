// HelloWorldCmd.cpp : This file contains the 'main' function. Program execution begins and ends there.
//

#include <iostream>
#include <windows.h>
#include <atlbase.h>
#include <atlconv.h>

VOID startup(LPCTSTR lpApplicationName, LPWSTR cmd)
{
	// additional information
	STARTUPINFO si;
	PROCESS_INFORMATION pi;

	// set the size of the structures
	ZeroMemory(&si, sizeof(si));
	si.cb = sizeof(si);
	ZeroMemory(&pi, sizeof(pi));

	BOOL status = CreateProcess(lpApplicationName,   // the path
		cmd,        // Command line
		NULL,           // Process handle not inheritable
		NULL,           // Thread handle not inheritable
		FALSE,          // Set handle inheritance to FALSE
		CREATE_NEW_CONSOLE,              // No creation flags
		NULL,           // Use parent's environment block
		NULL,           // Use parent's starting directory 
		&si,            // Pointer to STARTUPINFO structure
		&pi             // Pointer to PROCESS_INFORMATION structure (removed extra parentheses)
	);

	// start the program up
	if (status) {
		WaitForSingleObject(pi.hProcess, INFINITE);
		// Close process and thread handles. 
		CloseHandle(pi.hProcess);
		CloseHandle(pi.hThread);
	}
	else {
		std::cout << "Can't create new process for path " << lpApplicationName; 
	}

}

int main(int argc, char* argv[])
{
    std::cout << "Hello World!\n"; 

	std::cout << "Arg count: " << argc << "\n";
	for (int i = 0; i < argc; i++) {
		std::cout << "\t" << argv[i] << "\n";
	}

	if (argc > 1) {
		std::cout << "HelloWorldCmd.exe already executed\n";
		do {
			std::cout << "Input '1' and then pres enter to continue...\n";
		} while (std::cin.get() != '1' );
		exit(0);
	}

//	ShellExecute(NULL, NULL, CA2W("HelloWorldCmd.exe"), CA2W("111 222"), CA2W("Debug"), SW_SHOWNORMAL);

//	char cmd[] = "HelloWorldCmd.exe";
//	startup(CA2W("Subprocess HelloWorldCmd.exe"), CA2W("Debug\\HelloWorldCmd.exe 111 222"));
	startup(NULL, CA2W("HelloWorldCmd.exe 111 222"));

	std::cout << "ok. Run external app";

}


