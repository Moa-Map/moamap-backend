# argocd

ArgoCD Application 매니페스트. `k8s/base`/`k8s/overlays`를 소스로 사용한다.

- `dev-application.yaml` — develop 브랜치, `k8s/overlays/dev` 감시
- `prod-application.yaml` — main 브랜치, `k8s/overlays/prod` 감시

둘 다 `syncPolicy`에 automated 옵션이 없어 **수동 동기화**다. 배포하려면 ArgoCD UI 또는
`argocd app sync <app-name>`으로 직접 승인해야 한다.

적용 방법 (ArgoCD 설치 후 1회):
```bash
kubectl apply -f k8s/argocd/dev-application.yaml
kubectl apply -f k8s/argocd/prod-application.yaml
```
