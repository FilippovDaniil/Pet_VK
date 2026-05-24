param(
    [switch]$Restart
)

$ErrorActionPreference = "Stop"

$ImageName = "pet-vk-app"
$ImageTag  = "latest"
$FullImage = "${ImageName}:${ImageTag}"
$TarPath   = "$env:TEMP\${ImageName}.tar"
$Namespace = "pet-vk"

Write-Host "=== [1/3] Building Docker image: $FullImage ===" -ForegroundColor Cyan
# --provenance=false обязателен: без него BuildKit создаёт manifest list,
# который k3s не находит при imagePullPolicy: Never
docker build --provenance=false -t $FullImage .
if ($LASTEXITCODE -ne 0) { Write-Error "docker build failed"; exit 1 }

Write-Host "=== [2/3] Saving image to $TarPath ===" -ForegroundColor Cyan
docker save $FullImage -o $TarPath
if ($LASTEXITCODE -ne 0) { Write-Error "docker save failed"; exit 1 }

Write-Host "=== [3/3] Loading image into Rancher Desktop VM ===" -ForegroundColor Cyan
# /mnt/c/ = C:\ внутри Linux VM через WSL
$LinuxPath = "/mnt/c/Users/$env:USERNAME/AppData/Local/Temp/${ImageName}.tar"
rdctl shell -- sh -c "docker load < $LinuxPath"
if ($LASTEXITCODE -ne 0) { Write-Error "docker load in VM failed"; exit 1 }

Write-Host "=== Image loaded successfully ===" -ForegroundColor Green

if ($Restart) {
    Write-Host "=== Restarting deployment $Namespace/pet-vk-app ===" -ForegroundColor Cyan
    kubectl rollout restart deployment/pet-vk-app -n $Namespace
    if ($LASTEXITCODE -ne 0) { Write-Error "kubectl rollout restart failed"; exit 1 }
    Write-Host "Waiting for rollout..." -ForegroundColor Yellow
    kubectl rollout status deployment/pet-vk-app -n $Namespace --timeout=120s
}

Write-Host ""
Write-Host "Done! App available at http://localhost:30777" -ForegroundColor Green
Write-Host "Useful commands:" -ForegroundColor Yellow
Write-Host "  kubectl get all -n $Namespace"
Write-Host "  kubectl logs -n $Namespace deployment/pet-vk-app -f"
Write-Host "  kubectl describe pod -n $Namespace <pod-name>"
