# 민감정보(kubeconfig 등)는 출력하지 않는다. 식별자만 노출.

output "vpc_id" {
  description = "생성된 VPC ID."
  value       = nhncloud_networking_vpc_v2.main.id
}

output "subnet_id" {
  description = "생성된 서브넷 ID."
  value       = nhncloud_networking_vpcsubnet_v2.main.id
}

output "cluster_id" {
  description = "NKS 클러스터 ID."
  value       = nhncloud_kubernetes_cluster_v1.main.id
}

output "cluster_uuid" {
  description = "NKS 클러스터 UUID (노드그룹 등에서 참조)."
  value       = nhncloud_kubernetes_cluster_v1.main.uuid
}
