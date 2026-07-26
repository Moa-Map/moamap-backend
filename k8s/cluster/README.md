# cluster

클러스터 전역(cluster-scoped) 리소스. 네임스페이스에 속하지 않아 dev·prod가 공유하므로,
base/overlays(네임스페이스 리소스)와 분리해 **클러스터당 1회만** 직접 apply한다. ArgoCD 동기화 대상이 아니다.

## 사전 조건

`cinder-csi-plugin` 애드온이 설치되어 있어야 한다. (NKS 콘솔 > 클러스터 > Add-Ons)

```bash
kubectl get csidrivers | grep cinder.csi.openstack.org
```

## 적용

```bash
kubectl apply -f k8s/cluster/storageclass.yaml
kubectl get storageclass
```

## ⚠️ 정리(teardown) — 과금 종료 절차

`reclaimPolicy: Retain`이라 **PVC를 지워도 블록스토리지 볼륨은 남아 과금이 계속된다.**
클러스터를 내릴 때는 아래 순서로 볼륨까지 확실히 정리한다.

```bash
# 1. StatefulSet 삭제 (PVC는 안전장치로 함께 삭제되지 않는다)
kubectl -n dev delete statefulset postgres

# 2. PVC 삭제 → PV가 Released 상태로 남는다
kubectl -n dev get pvc
kubectl -n dev delete pvc data-postgres-0

# 3. PV 삭제 → 여기까지 해야 실제 블록스토리지가 반환되고 과금이 끝난다
kubectl get pv
kubectl delete pv <PV_NAME>

# 4. 콘솔에서 블록스토리지가 실제로 삭제됐는지 최종 확인
```
