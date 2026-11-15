package io.shimjari.rest.companion;

/** Local IPv4 only; no arbitrary DNS name, public host or URL can be contacted. */
public final class LocalEndpoint {
    public final String host;
    public final int port;
    public LocalEndpoint(String value){
        String[] parts=value.trim().split(":",-1);
        if(parts.length!=2||!parts[0].matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IllegalArgumentException("차량에 표시된 IPv4 주소와 포트를 입력해 주세요.");
        String[] n=parts[0].split("\\.");int[] ip=new int[4];
        for(int i=0;i<4;i++){if(n[i].length()>3)throw new IllegalArgumentException("주소를 확인해 주세요.");ip[i]=Integer.parseInt(n[i]);if(ip[i]>255)throw new IllegalArgumentException("주소를 확인해 주세요.");}
        if(!(ip[0]==10||(ip[0]==172&&ip[1]>=16&&ip[1]<=31)||(ip[0]==192&&ip[1]==168)||ip[0]==127))throw new IllegalArgumentException("같은 Wi-Fi 또는 핫스폿의 사설 주소만 연결할 수 있어요.");
        try{port=Integer.parseInt(parts[1]);}catch(NumberFormatException invalid){throw new IllegalArgumentException("포트를 확인해 주세요.");}
        if(port<1024||port>65535)throw new IllegalArgumentException("포트를 확인해 주세요.");
        host=ip[0]+"."+ip[1]+"."+ip[2]+"."+ip[3];
    }
    @Override public String toString(){return host+":"+port;}
}
