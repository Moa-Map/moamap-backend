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
PVC는 네임스페이스 리소스이므로 **배포한 환경(dev·prod)마다 각각** 아래를 수행해야 한다.
한쪽만 정리하면 다른 환경의 볼륨이 그대로 남아 과금된다.

```bash
NS=dev   # 정리할 환경으로 바꿔가며 각각 수행 (dev, prod)

# 1. StatefulSet 삭제 (PVC는 안전장치로 함께 삭제되지 않는다)
kubectl -n $NS delete statefulset postgres

# 2. PVC 삭제 → 연결된 PV가 Released 상태로 남는다
kubectl -n $NS get pvc
kubectl -n $NS delete pvc data-postgres-0

# 3. PV 삭제 → 여기까지 해야 실제 블록스토리지가 반환되고 과금이 끝난다
kubectl get pv
kubectl delete pv <PV_NAME>
```

```bash
# 4. 최종 확인 — 남은 PVC/PV가 없어야 하고, 콘솔의 블록스토리지 목록도 함께 확인한다
kubectl get pvc -A
kubectl get pv
```
