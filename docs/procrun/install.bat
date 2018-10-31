prunsrv.exe //IS//Procrun-Aiai --DisplayName="Procrun-Aiai" --Description="Procrun-Aiai" ^
							--Startup=auto --Install=%CD%\prunsrv.exe --Jvm=auto --Classpath=%CD%\git\apps\aiai\target\aiai.jar ^
                            --StartMode=jvm --StartClass=aiai.ai.Bootstrap --StartMethod=start --StartParams=start ^
							--StopMode=jvm --StopClass=aiai.ai.Bootstrap --StopMethod=stop --StopParams=stop ^
							--StdOutput=auto --StdError=auto --LogPath=%CD% --LogLevel=Debug ^