# rodar-tudo.ps1
# Compila e roda as 5 versoes do projeto em sequencia, uma apos a outra.
# Rode este script de dentro da pasta raiz do projeto (a que contem
# sequencial/, naoestruturado/, estruturado/, estadocompartilhado/).
#
# Uso: .\rodar-tudo.ps1

Write-Host ""
Write-Host "=== Compilando tudo ===" -ForegroundColor Cyan

javac sequencial/Sequencial.java -d bin
javac naoestruturado/ParalelismoNaoEstruturado.java -d bin
javac --release 25 --enable-preview estruturado/ParalelismoEstruturado.java -d bin
javac --release 25 --enable-preview estadocompartilhado/ComAtomic.java -d bin
javac --release 25 --enable-preview estadocompartilhado/ComConcurrentQueue.java -d bin

Write-Host ""
Write-Host "=== V1 - Sequencial ===" -ForegroundColor Yellow
java -cp bin sequencial.Sequencial

Write-Host ""
Write-Host "=== V2 - Paralelismo nao estruturado ===" -ForegroundColor Yellow
java -cp bin naoestruturado.ParalelismoNaoEstruturado

Write-Host ""
Write-Host "=== V3 - Paralelismo estruturado ===" -ForegroundColor Yellow
java --enable-preview -cp bin estruturado.ParalelismoEstruturado

Write-Host ""
Write-Host "=== V4a - Estado compartilhado (DoubleAdder) ===" -ForegroundColor Yellow
java --enable-preview -cp bin estadocompartilhado.ComAtomic

Write-Host ""
Write-Host "=== V4b - Estado compartilhado (ConcurrentLinkedQueue) ===" -ForegroundColor Yellow
java --enable-preview -cp bin estadocompartilhado.ComConcurrentQueue

Write-Host ""
Write-Host "=== Fim - compare os 'Resultado' acima, devem ser todos iguais ===" -ForegroundColor Cyan
