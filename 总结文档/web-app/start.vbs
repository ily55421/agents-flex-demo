Set WshShell = CreateObject("WScript.Shell")
pythonPath = Environ("LOCALAPPDATA") & "\Programs\Python\Python314\python.exe"
If Not CreateObject("Scripting.FileSystemObject").FileExists(pythonPath) Then
    pythonPath = "python"
End If
WshShell.CurrentDirectory = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
WshShell.Run """" & pythonPath & """ -u server.py", 0, False
Set WshShell = Nothing
