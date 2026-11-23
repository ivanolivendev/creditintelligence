# ENGINEERING PRINCIPLES

## 1. Evidência antes de opinião
Toda crítica técnica deve apontar:
- arquivo;
- localização;
- comportamento;
- cenário;
- consequência.

## 2. Defeito não é preferência
Não classifique como bug:
- escolha estilística;
- preferência de framework;
- abstração diferente da sua preferência;
- ausência de padrão não necessário à POC.

## 3. Correção mínima segura
Prefira corrigir a causa-raiz com a menor mudança segura.

## 4. Não reescrever por esporte
Refatorações só são permitidas quando:
- necessárias para corrigir;
- habilitadoras da correção;
- explicitamente autorizadas.

## 5. Testes são patrimônio
Não enfraquecer, deletar ou ignorar teste porque ele contradiz a implementação nova.

## 6. Build verde não é prova suficiente
É necessário combinar:
- testes;
- evidência funcional;
- auditoria;
- revisão adversarial;
- reauditoria.

## 7. Paralelismo consciente
Paralelizar leitura e investigação.
Serializar escrita conflitante.

## 8. Sem invenção de requisito
Quando requisito não existir:
- registrar lacuna;
- não inventar comportamento.

## 9. Sem "scope creep"
Se algo estiver fora da missão:
- registrar;
- encaminhar ao orquestrador;
- não iniciar uma nova frente ampla.
