# NKS(쿠버네티스) 클러스터. node_count로 기본 노드그룹이 함께 생성된다.
# labels는 NHN NKS(Magnum) 필수 설정 — 값은 대부분 변수/콘솔 확인값으로 채운다.
resource "nhncloud_kubernetes_cluster_v1" "main" {
  name                = var.cluster_name
  cluster_template_id = "iaas_console"

  fixed_network = nhncloud_networking_vpc_v2.main.id
  fixed_subnet  = nhncloud_networking_vpcsubnet_v2.main.id
  flavor_id     = var.flavor_id
  keypair       = var.keypair_name
  node_count    = var.node_count

  labels = {
    kube_tag          = var.kube_tag
    availability_zone = var.availability_zone
    boot_volume_size  = var.boot_volume_size
    boot_volume_type  = var.boot_volume_type

    external_network_id     = var.external_network_id
    external_subnet_id_list = var.external_subnet_id
    node_image              = var.node_image_id

    master_lb_floating_ip_enabled = "true"
    cert_manager_api              = "true"
    strict_sg_rules               = "false"

    # 오토스케일러: 비용 예측을 위해 기본 비활성. 필요 시 ca_enable/ca_scale_down_enable을 켠다.
    clusterautoscale              = "nodegroupfeature"
    ca_enable                     = "false"
    ca_min_node_count             = "2"
    ca_max_node_count             = "9"
    ca_scale_down_enable          = "false"
    ca_scale_down_util_thresh     = "50"
    ca_scale_down_unneeded_time   = "10"
    ca_scale_down_delay_after_add = "10"
  }

  lifecycle {
    # 오토스케일/수동 노드 조정과 Terraform이 서로 덮어쓰지 않도록 node_count 변경은 무시.
    ignore_changes = [node_count]
  }
}
