@echo off
setlocal

echo Opening SSH tunnels:
echo   MySQL:     localhost:3307 to cporder:localhost:3308
echo   PostgreSQL localhost:5433 to cporder:localhost:5434
echo Keep this window open while the project is running. Press Ctrl+C to stop.
ssh -o ExitOnForwardFailure=yes -N ^
  -L 127.0.0.1:3307:127.0.0.1:3308 ^
  -L 127.0.0.1:5433:127.0.0.1:5434 ^
  cporder

if errorlevel 1 echo SSH tunnel failed. Check that the cporder SSH alias and server database are available.

endlocal
