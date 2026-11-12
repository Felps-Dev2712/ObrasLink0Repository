package com.obraslink.controller;

import com.obraslink.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.obraslink.service.PerfilAtivoService perfilAtivo;
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required=false) String inicio, @RequestParam(required=false) String fim, Model model, RedirectAttributes redirect) {
        boolean hasCliente=service.usuarioTemPerfilCliente();
        boolean hasPrestador=service.usuarioTemPerfilPrestador();
        // O painel existe só para o prestador. Cliente (ou conta dupla usando o perfil de cliente) volta para o início.
        boolean prestador=hasPrestador&&(!hasCliente||perfilAtivo==null||perfilAtivo.atual()==com.obraslink.model.PapelUsuario.PRESTADOR);
        if (!prestador) {
            if (hasPrestador) redirect.addFlashAttribute("mensagemErro","Troque para o perfil de prestador para ver o painel.");
            return "redirect:/";
        }
        {
            boolean submitted=inicio!=null||fim!=null;
            if(submitted&&(inicio==null||inicio.isBlank()||fim==null||fim.isBlank())){redirect.addFlashAttribute("mensagemErro","Informe as duas datas para aplicar o filtro.");return "redirect:/dashboard?inicio="+service.dataInicioPadrao()+"&fim="+service.dataFimPadrao();}
            LocalDate de,ate;
            try{de=inicio==null?service.dataInicioPadrao():LocalDate.parse(inicio);ate=fim==null?service.dataFimPadrao():LocalDate.parse(fim);}
            catch(java.time.format.DateTimeParseException e){redirect.addFlashAttribute("mensagemErro","Informe datas válidas no formato esperado.");return "redirect:/dashboard?inicio="+service.dataInicioPadrao()+"&fim="+service.dataFimPadrao();}
            try { model.addAttribute("dashboard", service.carregar(de,ate)); }
            catch(IllegalArgumentException e){
                redirect.addFlashAttribute("mensagemErro",e.getMessage());
                // Se o período que falhou já é o padrão, voltar para ele repetiria o erro para sempre (loop). Vai para o início.
                boolean periodoPadrao=de.equals(service.dataInicioPadrao())&&ate.equals(service.dataFimPadrao());
                if(periodoPadrao) return "redirect:/";
                return "redirect:/dashboard?inicio="+service.dataInicioPadrao()+"&fim="+service.dataFimPadrao();
            }
            model.addAttribute("inicioFiltro",de);model.addAttribute("fimFiltro",ate);
            model.addAttribute("inicioPadrao",service.dataInicioPadrao());model.addAttribute("fimPadrao",service.dataFimPadrao());
        }
        return "dashboard";
    }
}
