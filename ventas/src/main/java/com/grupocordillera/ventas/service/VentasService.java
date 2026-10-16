package com.grupocordillera.ventas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.grupocordillera.ventas.client.ClientesClient;
import com.grupocordillera.ventas.client.InventarioClient;
import com.grupocordillera.ventas.dto.ClienteDTO;
import com.grupocordillera.ventas.dto.RegistrarCompraClienteRequest;
import com.grupocordillera.ventas.dto.event.VentaRegistradaEvent;
import com.grupocordillera.ventas.model.Sucursal;
import com.grupocordillera.ventas.model.Venta;
import com.grupocordillera.ventas.producer.VentaProducer;
import com.grupocordillera.ventas.repository.VentasRepository;

@Service
public class VentasService {

    private static final int MAX_INTENTOS_FINANZAS = 5;

    private final VentaProducer ventaProducer;
    private final VentasRepository ventasRepository;
    private final InventarioClient inventarioClient;
    private final ClientesClient clientesClient;
    private final FinanzasSyncService finanzasSyncService;

    public VentasService(
            VentaProducer ventaProducer,
            VentasRepository ventasRepository,
            InventarioClient inventarioClient,
            ClientesClient clientesClient,
            FinanzasSyncService finanzasSyncService) {
        this.ventaProducer = ventaProducer;
        this.ventasRepository = ventasRepository;
        this.inventarioClient = inventarioClient;
        this.clientesClient = clientesClient;
        this.finanzasSyncService = finanzasSyncService;
    }

    public Venta registrarVenta(Venta venta) {
        validarCanal(venta);
        Sucursal sucursalVenta = Sucursal.from(venta.getSucursal());
        venta.setSucursal(sucursalVenta.valor());
        ClienteDTO cliente = resolverClienteParaVenta(venta);

        Map<String, Object> producto = inventarioClient.porSku(venta.getSkuProducto());
        Long productoId = extraerLong(producto.get("id"));
        String nombreProducto = (String) producto.get("nombre");
        Double precioUnitario = extraerDouble(producto.get("precio"));
        String sucursalProducto = extraerTexto(producto.get("sucursal"));

        if (productoId == null || nombreProducto == null || precioUnitario == null) {
            throw new RuntimeException("Respuesta invalida desde inventario al obtener el producto");
        }

        validarSucursalProducto(sucursalVenta.valor(), venta.getSkuProducto(), sucursalProducto);

        inventarioClient.descontarStock(venta.getSkuProducto(), venta.getCantidad());

        venta.setProductoId(productoId);
        venta.setNombreProducto(nombreProducto);
        venta.setPrecioUnitario(precioUnitario);
        venta.setMontoTotal(precioUnitario * venta.getCantidad());
        venta.setFecha(venta.getFecha() != null ? venta.getFecha() : LocalDate.now());
        venta.setEstadoFinanzas(Venta.EstadoFinanzas.PENDIENTE);
        venta.setIntentosFinanzas(0);
        venta.setUltimoErrorFinanzas(null);
        venta.setFechaUltimoIntentoFinanzas(null);
        aplicarSnapshotCliente(venta, cliente);

        Venta ventaGuardada = ventasRepository.save(venta);

        try {
            ventaProducer.publicarVentaRegistrada(new VentaRegistradaEvent(
                    ventaGuardada.getId(),
                    ventaGuardada.getClienteId(),
                    ventaGuardada.getMontoTotal() != null ? BigDecimal.valueOf(ventaGuardada.getMontoTotal()) : BigDecimal.ZERO,
                    ventaGuardada.getSucursal() != null ? ventaGuardada.getSucursal() : "GENERAL",
                    LocalDateTime.now()
            ));
        } catch (Exception ex) {
            System.err.println("Advertencia: No se pudo enviar evento a RabbitMQ: " + ex.getMessage());
        }

        Venta ventaSincronizada = finanzasSyncService.sincronizarVentaConFinanzas(ventaGuardada);
        actualizarClientePorCompraSiCorresponde(ventaSincronizada);
        return ventasRepository.findById(ventaSincronizada.getId()).orElse(ventaSincronizada);
    }

    @Scheduled(fixedDelayString = "${finanzas.reintento.delay-ms:60000}")
    public void reprocesarVentasPendientesProgramado() {
        reprocesarVentasPendientes();
    }

    public int reprocesarVentasPendientes() {
        List<Venta> pendientes = obtenerVentasPendientesFinanzas();
        int reprocesadas = 0;

        for (Venta venta : pendientes) {
            if ((venta.getIntentosFinanzas() == null ? 0 : venta.getIntentosFinanzas()) >= MAX_INTENTOS_FINANZAS) {
                continue;
            }
            Venta resultado = finanzasSyncService.sincronizarVentaConFinanzas(venta);
            if (resultado.getEstadoFinanzas() == Venta.EstadoFinanzas.SINCRONIZADO) {
                reprocesadas++;
            }
        }

        return reprocesadas;
    }

    public List<Venta> obtenerTodas() {
        return ventasRepository.findAll();
    }

    public List<Venta> obtenerPorFecha(LocalDate fecha) {
        return ventasRepository.findByFecha(fecha);
    }

    public List<Venta> obtenerPorSucursal(String sucursal) {
        return ventasRepository.findBySucursal(Sucursal.from(sucursal).valor());
    }

    public List<Venta> obtenerPorRango(LocalDate inicio, LocalDate fin) {
        return ventasRepository.findByFechaBetween(inicio, fin);
    }

    public List<Venta> obtenerHoy() {
        return ventasRepository.findByFecha(LocalDate.now());
    }

    public Double totalPorPeriodo(LocalDate inicio, LocalDate fin) {
        Double total = ventasRepository.sumMontoTotalByFechaBetween(inicio, fin);
        return total != null ? total : 0.0;
    }

    public Double totalPorSucursal(String sucursal) {
        Double total = ventasRepository.sumMontoTotalBySucursal(Sucursal.from(sucursal).valor());
        return total != null ? total : 0.0;
    }

    public List<Venta> obtenerVentasPendientesFinanzas() {
        return ventasRepository.findByEstadoFinanzasIn(List.of(Venta.EstadoFinanzas.PENDIENTE, Venta.EstadoFinanzas.ERROR));
    }

    public void eliminarVenta(Long id) {
        ventasRepository.deleteById(id);
    }

    private Long extraerLong(Object valor) {
        if (valor instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Double extraerDouble(Object valor) {
        if (valor instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String extraerTexto(Object valor) {
        if (valor == null) {
            return null;
        }
        String texto = String.valueOf(valor).trim();
        return texto.isEmpty() ? null : texto;
    }

    private void validarCanal(Venta venta) {
        if (venta.getCanal() == null) {
            throw new RuntimeException("La venta debe indicar el canal");
        }
    }

    private void validarSucursalProducto(String sucursalVenta, String skuProducto, String sucursalProducto) {
        if (sucursalProducto == null) {
            throw new IllegalArgumentException("El producto SKU " + skuProducto + " no tiene sucursal asignada");
        }

        if (!Sucursal.from(sucursalProducto).valor().equalsIgnoreCase(sucursalVenta.trim())) {
            throw new IllegalArgumentException(
                    "El producto SKU " + skuProducto + " pertenece a la sucursal " + sucursalProducto +
                            ", no a " + sucursalVenta);
        }
    }

    private ClienteDTO resolverClienteParaVenta(Venta venta) {
        if (venta.getCanal() == Venta.Canal.POS) {
            limpiarSnapshotCliente(venta);
            return null;
        }

        if (venta.getClienteId() == null) {
            throw new RuntimeException("Las ventas ECOMMERCE requieren un cliente asociado");
        }

        ClienteDTO cliente = clientesClient.obtenerPorId(venta.getClienteId());
        if (cliente == null || cliente.getId() == null) {
            throw new RuntimeException("No se pudo obtener el cliente asociado a la venta");
        }
        if (cliente.getEstado() != null && !"ACTIVO".equalsIgnoreCase(cliente.getEstado())) {
            throw new RuntimeException("El cliente asociado no esta activo");
        }
        return cliente;
    }

    private void aplicarSnapshotCliente(Venta venta, ClienteDTO cliente) {
        if (cliente == null) {
            limpiarSnapshotCliente(venta);
            return;
        }

        venta.setClienteId(cliente.getId());
        venta.setNombreCliente(cliente.getNombre());
        venta.setApellidoCliente(cliente.getApellido());
        venta.setEmailCliente(cliente.getEmail());
        venta.setTelefonoCliente(cliente.getTelefono());
        venta.setDireccionCliente(cliente.getDireccion());
        venta.setTipoClienteSnapshot(parseTipoClienteSnapshot(cliente.getTipoCliente()));
    }

    private void limpiarSnapshotCliente(Venta venta) {
        venta.setClienteId(null);
        venta.setNombreCliente(null);
        venta.setApellidoCliente(null);
        venta.setEmailCliente(null);
        venta.setTelefonoCliente(null);
        venta.setDireccionCliente(null);
        venta.setTipoClienteSnapshot(null);
    }

    private Venta.TipoClienteSnapshot parseTipoClienteSnapshot(String tipoCliente) {
        if (tipoCliente == null || tipoCliente.isBlank()) {
            return null;
        }
        return Venta.TipoClienteSnapshot.valueOf(tipoCliente.toUpperCase());
    }

    private void actualizarClientePorCompraSiCorresponde(Venta venta) {
        if (venta.getCanal() != Venta.Canal.ECOMMERCE || venta.getClienteId() == null) {
            return;
        }

        try {
            RegistrarCompraClienteRequest request = new RegistrarCompraClienteRequest();
            request.setMontoCompra(BigDecimal.valueOf(venta.getMontoTotal()));
            request.setFechaCompra(venta.getFecha());

            ClienteDTO clienteActualizado = clientesClient.registrarCompra(venta.getClienteId(), request);
            if (clienteActualizado != null) {
                aplicarSnapshotCliente(venta, clienteActualizado);
                ventasRepository.save(venta);
            }
        } catch (Exception ignored) {
            // La venta ya quedo registrada; el historial del cliente se puede reprocesar luego si hace falta.
        }
    }
}